"""Dashboard Kits page (staff+): the runner gear rules (which slots must not
be empty; bow/crossbow rule) plus named recommended kits - gear per slot and
an inventory with quantities - that the plugin uses for its local
pre-course check and bank highlighting. Replaced the old per-slot loadout
rule editor on 2026-10-27."""

from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, HTTPException, Query, Request
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.fc_group import FcGroup
from app.models.item import Item
from app.models.kit import INVENTORY, Kit, KitItem
from app.models.user import User, UserRole
from app.services.gear_rules import EMPTY_CHECK_SLOTS, SLOT_LABELS, SLOT_ORDER, required_slots
from app.web.deps import (
    get_csrf_token,
    get_default_fc_group,
    require_role,
    template_context,
    verify_csrf,
)
from app.web.templating import templates

router = APIRouter()

MAX_INVENTORY_LINES = 28
MAX_QTY = 100_000


def item_icon_url(name: str) -> str:
    """OSRS Wiki's own Special:FilePath redirect - resolves real item sprite
    images by name without needing a local icon library."""
    filename = quote(name.replace(" ", "_"))
    return f"https://oldschool.runescape.wiki/w/Special:FilePath/{filename}.png"


def _check_slot(slot: str) -> str:
    slot = slot.upper()
    if slot not in SLOT_ORDER:
        raise HTTPException(status_code=404, detail="Unknown slot")
    return slot


async def _get_kit(session: AsyncSession, group: FcGroup, kit_id: int) -> Kit:
    kit = (
        await session.execute(select(Kit).where(Kit.id == kit_id, Kit.fc_group_id == group.id))
    ).scalar_one_or_none()
    if kit is None:
        raise HTTPException(status_code=404, detail="Kit not found")
    return kit


async def _item_lookup(session: AsyncSession, item_ids: list[int]) -> dict[int, dict]:
    if not item_ids:
        return {}
    rows = (await session.execute(select(Item).where(Item.id.in_(item_ids)))).scalars().all()
    return {i.id: {"id": i.id, "name": i.name, "icon": item_icon_url(i.name)} for i in rows}


async def _require_item(session: AsyncSession, item_id: int) -> None:
    if (await session.execute(select(Item).where(Item.id == item_id))).scalar_one_or_none() is None:
        raise HTTPException(status_code=404, detail="Unknown item")


async def _slot_context(session: AsyncSession, kit: Kit, slot: str) -> dict:
    row = (
        await session.execute(select(KitItem).where(KitItem.kit_id == kit.id, KitItem.slot == slot))
    ).scalar_one_or_none()
    item = None
    if row is not None:
        item = (await _item_lookup(session, [row.item_id])).get(
            row.item_id, {"id": row.item_id, "name": f"Item {row.item_id}", "icon": ""}
        )
    return {"slot": slot, "label": SLOT_LABELS[slot], "item": item, "kit_id": kit.id}


async def _inventory_context(session: AsyncSession, kit: Kit) -> dict:
    rows = (
        (
            await session.execute(
                select(KitItem)
                .where(KitItem.kit_id == kit.id, KitItem.slot == INVENTORY)
                .order_by(KitItem.id)
            )
        )
        .scalars()
        .all()
    )
    lookup = await _item_lookup(session, [r.item_id for r in rows])
    lines = [
        {
            "id": r.id,
            "qty": r.qty,
            "item": lookup.get(
                r.item_id, {"id": r.item_id, "name": f"Item {r.item_id}", "icon": ""}
            ),
        }
        for r in rows
    ]
    return {"kit_id": kit.id, "lines": lines, "full": len(lines) >= MAX_INVENTORY_LINES}


def _fragment(request: Request, name: str, ctx: dict):
    ctx.update({"request": request, "csrf_token": get_csrf_token(request)})
    return templates.TemplateResponse(request, name, ctx)


async def _slot_response(request: Request, session: AsyncSession, kit: Kit, slot: str):
    if request.headers.get("HX-Request") == "true":
        return _fragment(request, "kits/_slot.html", await _slot_context(session, kit, slot))
    return RedirectResponse(url=f"/kits/{kit.id}", status_code=303)


async def _inventory_response(request: Request, session: AsyncSession, kit: Kit):
    if request.headers.get("HX-Request") == "true":
        return _fragment(request, "kits/_inventory.html", await _inventory_context(session, kit))
    return RedirectResponse(url=f"/kits/{kit.id}", status_code=303)


async def _search(session: AsyncSession, q: str) -> list[dict]:
    if not q:
        return []
    rows = (
        (await session.execute(select(Item).where(Item.name.ilike(f"%{q}%")).limit(20)))
        .scalars()
        .all()
    )
    return [{"id": i.id, "name": i.name, "icon": item_icon_url(i.name)} for i in rows]


# --- list page + gear rules ---------------------------------------------------


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
    current = set(required_slots(group))
    rule_slots = [(s, SLOT_LABELS[s], s in current) for s in EMPTY_CHECK_SLOTS]
    context = template_context(
        request,
        user,
        kits=kits,
        rule_slots=rule_slots,
        ranged_weapon=group.require_ranged_weapon,
    )
    return templates.TemplateResponse(request, "kits/list.html", context)


@router.post("/kits/rules")
async def save_rules(
    required: list[str] = Form(default=[]),
    ranged_weapon: bool = Form(False),
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    group.required_slots = [s for s in EMPTY_CHECK_SLOTS if s in required]
    group.require_ranged_weapon = ranged_weapon
    await session.commit()
    return RedirectResponse(url="/kits", status_code=303)


@router.post("/kits")
async def create_kit(
    name: str = Form(...),
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    name = name.strip()[:64]
    if not name:
        raise HTTPException(status_code=422, detail="Name required")
    has_default = (
        await session.execute(
            select(Kit).where(Kit.fc_group_id == group.id, Kit.is_default.is_(True))
        )
    ).scalars().first() is not None
    kit = Kit(fc_group_id=group.id, name=name, is_default=not has_default)
    session.add(kit)
    await session.commit()
    return RedirectResponse(url=f"/kits/{kit.id}", status_code=303)


@router.post("/kits/{kit_id}/rename")
async def rename_kit(
    kit_id: int,
    name: str = Form(...),
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    kit = await _get_kit(session, group, kit_id)
    if name.strip():
        kit.name = name.strip()[:64]
        await session.commit()
    return RedirectResponse(url=f"/kits/{kit.id}", status_code=303)


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


# --- kit editor ---------------------------------------------------------------


@router.get("/kits/{kit_id}")
async def kit_detail(
    kit_id: int,
    request: Request,
    user: User = Depends(require_role(UserRole.STAFF)),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    kit = await _get_kit(session, group, kit_id)
    slots = [await _slot_context(session, kit, slot) for slot in SLOT_ORDER]
    context = template_context(
        request, user, kit=kit, slots=slots, **(await _inventory_context(session, kit))
    )
    return templates.TemplateResponse(request, "kits/detail.html", context)


@router.post("/kits/{kit_id}/slots/{slot}/item")
async def set_slot_item(
    kit_id: int,
    slot: str,
    request: Request,
    item_id: int = Form(...),
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    slot = _check_slot(slot)
    kit = await _get_kit(session, group, kit_id)
    await _require_item(session, item_id)
    row = (
        await session.execute(select(KitItem).where(KitItem.kit_id == kit.id, KitItem.slot == slot))
    ).scalar_one_or_none()
    if row is None:
        session.add(KitItem(kit_id=kit.id, slot=slot, item_id=item_id, qty=1))
    else:
        row.item_id = item_id
    await session.commit()
    return await _slot_response(request, session, kit, slot)


@router.post("/kits/{kit_id}/slots/{slot}/clear")
async def clear_slot(
    kit_id: int,
    slot: str,
    request: Request,
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    slot = _check_slot(slot)
    kit = await _get_kit(session, group, kit_id)
    for row in (
        await session.execute(select(KitItem).where(KitItem.kit_id == kit.id, KitItem.slot == slot))
    ).scalars():
        await session.delete(row)
    await session.commit()
    return await _slot_response(request, session, kit, slot)


@router.get("/kits/{kit_id}/slots/{slot}/search")
async def search_slot(
    kit_id: int,
    slot: str,
    request: Request,
    q: str = Query(default=""),
    user: User = Depends(require_role(UserRole.STAFF)),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    slot = _check_slot(slot)
    kit = await _get_kit(session, group, kit_id)
    context = template_context(
        request, user, results=await _search(session, q), kit_id=kit.id, slot=slot, query=q
    )
    return templates.TemplateResponse(request, "kits/_slot_search.html", context)


@router.post("/kits/{kit_id}/inventory")
async def add_inventory(
    kit_id: int,
    request: Request,
    item_id: int = Form(...),
    qty: int = Form(1),
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    kit = await _get_kit(session, group, kit_id)
    await _require_item(session, item_id)
    qty = max(1, min(qty, MAX_QTY))
    rows = (
        (
            await session.execute(
                select(KitItem).where(KitItem.kit_id == kit.id, KitItem.slot == INVENTORY)
            )
        )
        .scalars()
        .all()
    )
    existing = next((r for r in rows if r.item_id == item_id), None)
    if existing is not None:
        existing.qty = min(existing.qty + qty, MAX_QTY)
    elif len(rows) < MAX_INVENTORY_LINES:
        session.add(KitItem(kit_id=kit.id, slot=INVENTORY, item_id=item_id, qty=qty))
    await session.commit()
    return await _inventory_response(request, session, kit)


@router.post("/kits/{kit_id}/inventory/{line_id}/qty")
async def set_inventory_qty(
    kit_id: int,
    line_id: int,
    request: Request,
    qty: int = Form(...),
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    kit = await _get_kit(session, group, kit_id)
    row = (
        await session.execute(
            select(KitItem).where(
                KitItem.id == line_id, KitItem.kit_id == kit.id, KitItem.slot == INVENTORY
            )
        )
    ).scalar_one_or_none()
    if row is not None:
        row.qty = max(1, min(qty, MAX_QTY))
        await session.commit()
    return await _inventory_response(request, session, kit)


@router.post("/kits/{kit_id}/inventory/{line_id}/delete")
async def delete_inventory(
    kit_id: int,
    line_id: int,
    request: Request,
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    kit = await _get_kit(session, group, kit_id)
    row = (
        await session.execute(
            select(KitItem).where(
                KitItem.id == line_id, KitItem.kit_id == kit.id, KitItem.slot == INVENTORY
            )
        )
    ).scalar_one_or_none()
    if row is not None:
        await session.delete(row)
        await session.commit()
    return await _inventory_response(request, session, kit)


@router.get("/kits/{kit_id}/inventory/search")
async def search_inventory(
    kit_id: int,
    request: Request,
    q: str = Query(default=""),
    user: User = Depends(require_role(UserRole.STAFF)),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    kit = await _get_kit(session, group, kit_id)
    context = template_context(
        request, user, results=await _search(session, q), kit_id=kit.id, query=q
    )
    return templates.TemplateResponse(request, "kits/_inventory_search.html", context)
