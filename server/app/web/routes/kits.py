"""Dashboard Kits page (staff+): named recommended runner setups - gear per
slot and an inventory with quantities - that the plugin uses for its local
pre-course check and bank highlighting. The gear *rules* runners are flagged
against live on the Settings page.

The editor is one form: picking items only changes the page, and nothing is
written until Save, which replaces the kit's contents wholesale."""

from urllib.parse import quote

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.fc_group import FcGroup
from app.models.item import Item
from app.models.kit import COINS_ITEM_ID, COURSE_FEE_COINS, INVENTORY, Kit, KitItem
from app.models.user import User, UserRole
from app.services.gear_rules import SLOT_LABELS, SLOT_ORDER
from app.web.deps import get_default_fc_group, require_role, template_context, verify_csrf
from app.web.templating import templates

router = APIRouter()

# 28 slots, one of which always holds the course entry fee.
MAX_INVENTORY_LINES = 27
MAX_QTY = 100_000
MAX_NAME = 64


def item_icon_url(name: str) -> str:
    """OSRS Wiki's own Special:FilePath redirect - resolves real item sprite
    images by name without needing a local icon library."""
    filename = quote(name.replace(" ", "_"))
    return f"https://oldschool.runescape.wiki/w/Special:FilePath/{filename}.png"


async def _get_kit(session: AsyncSession, group: FcGroup, kit_id: int) -> Kit:
    kit = (
        await session.execute(select(Kit).where(Kit.id == kit_id, Kit.fc_group_id == group.id))
    ).scalar_one_or_none()
    if kit is None:
        raise HTTPException(status_code=404, detail="Kit not found")
    return kit


async def _items_by_id(session: AsyncSession, item_ids: set[int]) -> dict[int, Item]:
    if not item_ids:
        return {}
    rows = (await session.execute(select(Item).where(Item.id.in_(item_ids)))).scalars().all()
    return {item.id: item for item in rows}


def _item_view(item_id: int, items: dict[int, Item]) -> dict:
    item = items.get(item_id)
    name = item.name if item is not None else f"Item {item_id}"
    return {"id": item_id, "name": name, "icon": item_icon_url(name) if item else ""}


async def _editor(
    request: Request, user: User, session: AsyncSession | None, kit: Kit | None, **extra
):
    rows = []
    if kit is not None and session is not None:
        rows = (
            (
                await session.execute(
                    select(KitItem).where(KitItem.kit_id == kit.id).order_by(KitItem.id)
                )
            )
            .scalars()
            .all()
        )
    items = await _items_by_id(session, {r.item_id for r in rows}) if rows else {}
    by_slot = {r.slot: r for r in rows if r.slot != INVENTORY}
    slots = [
        {
            "slot": slot,
            "label": SLOT_LABELS[slot],
            "item": _item_view(by_slot[slot].item_id, items) if slot in by_slot else None,
        }
        for slot in SLOT_ORDER
    ]
    inventory = [
        {"item": _item_view(r.item_id, items), "qty": r.qty}
        for r in rows
        if r.slot == INVENTORY and r.item_id != COINS_ITEM_ID
    ]
    fee = {
        "item": {"id": COINS_ITEM_ID, "name": "Coins", "icon": item_icon_url("Coins 10000")},
        "qty": COURSE_FEE_COINS,
    }
    context = template_context(
        request,
        user,
        kit=kit,
        slots=slots,
        inventory=inventory,
        fee=fee,
        max_lines=MAX_INVENTORY_LINES,
        **extra,
    )
    return templates.TemplateResponse(request, "kits/edit.html", context)


async def _apply_form(session: AsyncSession, request: Request, kit: Kit) -> None:
    """Replaces the kit's name, gear and inventory with what the editor posted."""
    form = await request.form()
    name = str(form.get("name", "")).strip()[:MAX_NAME]
    if not name:
        raise HTTPException(status_code=422, detail="The kit needs a name")

    equipment: dict[str, int] = {}
    for slot in SLOT_ORDER:
        raw = str(form.get(f"slot_{slot}", "")).strip()
        if raw:
            equipment[slot] = int(raw)

    inventory: dict[int, int] = {}
    ids = form.getlist("inv_item")
    qtys = form.getlist("inv_qty")
    for raw_id, raw_qty in zip(ids, qtys, strict=False):
        try:
            item_id, qty = int(raw_id), int(raw_qty)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail="Bad inventory line") from exc
        if item_id == COINS_ITEM_ID:
            continue  # the entry fee is fixed and always added - see app.models.kit
        inventory[item_id] = min(inventory.get(item_id, 0) + max(1, qty), MAX_QTY)
    if len(inventory) > MAX_INVENTORY_LINES:
        raise HTTPException(
            status_code=422,
            detail=f"A kit can carry at most {MAX_INVENTORY_LINES} items besides the entry fee",
        )

    known = await _items_by_id(session, set(equipment.values()) | set(inventory))
    if any(i not in known for i in [*equipment.values(), *inventory]):
        raise HTTPException(status_code=404, detail="Unknown item")

    kit.name = name
    for old in (await session.execute(select(KitItem).where(KitItem.kit_id == kit.id))).scalars():
        await session.delete(old)
    await session.flush()
    for slot, item_id in equipment.items():
        session.add(KitItem(kit_id=kit.id, slot=slot, item_id=item_id, qty=1))
    for item_id, qty in inventory.items():
        session.add(KitItem(kit_id=kit.id, slot=INVENTORY, item_id=item_id, qty=qty))


@router.get("/kits")
async def list_kits(
    request: Request,
    user: User = Depends(require_role(UserRole.STAFF)),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    kits = (
        (await session.execute(select(Kit).where(Kit.fc_group_id == group.id).order_by(Kit.name)))
        .scalars()
        .all()
    )
    context = template_context(request, user, kits=kits)
    return templates.TemplateResponse(request, "kits/list.html", context)


@router.get("/kits/new")
async def new_kit(request: Request, user: User = Depends(require_role(UserRole.STAFF))):
    return await _editor(request, user, None, None)


@router.post("/kits")
async def create_kit(
    request: Request,
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    has_default = (
        await session.execute(
            select(Kit).where(Kit.fc_group_id == group.id, Kit.is_default.is_(True))
        )
    ).scalars().first() is not None
    kit = Kit(fc_group_id=group.id, name="", is_default=not has_default)
    session.add(kit)
    await session.flush()
    await _apply_form(session, request, kit)
    await session.commit()
    return RedirectResponse(url="/kits", status_code=303)


@router.get("/kits/search")
async def search_items(
    request: Request,
    q: str = Query(default=""),
    target: str = Query(default=INVENTORY),
    user: User = Depends(require_role(UserRole.STAFF)),
    session: AsyncSession = Depends(get_session),
):
    target = target.upper()
    if target != INVENTORY and target not in SLOT_ORDER:
        raise HTTPException(status_code=404, detail="Unknown slot")
    results = []
    if q:
        rows = (
            (await session.execute(select(Item).where(Item.name.ilike(f"%{q}%")).limit(20)))
            .scalars()
            .all()
        )
        results = [{"id": i.id, "name": i.name, "icon": item_icon_url(i.name)} for i in rows]
    context = template_context(request, user, results=results, target=target, query=q)
    return templates.TemplateResponse(request, "kits/_search.html", context)


@router.get("/kits/{kit_id}")
async def kit_redirect(kit_id: int, user: User = Depends(require_role(UserRole.STAFF))):
    return RedirectResponse(url=f"/kits/{kit_id}/edit", status_code=303)


@router.get("/kits/{kit_id}/edit")
async def edit_kit(
    kit_id: int,
    request: Request,
    user: User = Depends(require_role(UserRole.STAFF)),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    kit = await _get_kit(session, group, kit_id)
    return await _editor(request, user, session, kit)


@router.post("/kits/{kit_id}")
async def save_kit(
    kit_id: int,
    request: Request,
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    kit = await _get_kit(session, group, kit_id)
    await _apply_form(session, request, kit)
    await session.commit()
    return RedirectResponse(url="/kits", status_code=303)


@router.post("/kits/{kit_id}/default")
async def make_default(
    kit_id: int,
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    kit = await _get_kit(session, group, kit_id)
    for other in (await session.execute(select(Kit).where(Kit.fc_group_id == group.id))).scalars():
        other.is_default = other.id == kit.id
    await session.commit()
    return RedirectResponse(url="/kits", status_code=303)


@router.post("/kits/{kit_id}/delete")
async def delete_kit(
    kit_id: int,
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    kit = await _get_kit(session, group, kit_id)
    was_default = kit.is_default
    for item in (await session.execute(select(KitItem).where(KitItem.kit_id == kit.id))).scalars():
        await session.delete(item)
    await session.delete(kit)
    await session.flush()
    if was_default:
        replacement = (
            (
                await session.execute(
                    select(Kit).where(Kit.fc_group_id == group.id).order_by(Kit.name)
                )
            )
            .scalars()
            .first()
        )
        if replacement is not None:
            replacement.is_default = True
    await session.commit()
    return RedirectResponse(url="/kits", status_code=303)
