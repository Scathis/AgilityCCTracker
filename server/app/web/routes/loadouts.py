from urllib.parse import quote

from fastapi import APIRouter, Depends, Form, HTTPException, Query, Request
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.fc_group import FcGroup
from app.models.item import Item
from app.models.loadout import Loadout, LoadoutRequirement
from app.models.user import User, UserRole
from app.services.loadouts import SLOT_ORDER
from app.web.deps import (
    get_csrf_token,
    get_default_fc_group,
    require_role,
    template_context,
    verify_csrf,
)
from app.web.templating import templates

router = APIRouter()


def item_icon_url(name: str) -> str:
    """OSRS Wiki's own Special:FilePath redirect - resolves real item sprite
    images by name without needing a local icon library (confirmed working
    for names with spaces/parentheses, e.g. "Studded body (g)")."""
    filename = quote(name.replace(" ", "_"))
    return f"https://oldschool.runescape.wiki/w/Special:FilePath/{filename}.png"


def _check_slot(slot: str) -> str:
    slot = slot.upper()
    if slot not in SLOT_ORDER:
        raise HTTPException(status_code=404, detail="Unknown slot")
    return slot


async def _get_loadout(session: AsyncSession, loadout_id: int) -> Loadout:
    result = await session.execute(select(Loadout).where(Loadout.id == loadout_id))
    loadout = result.scalar_one_or_none()
    if loadout is None:
        raise HTTPException(status_code=404, detail="Loadout not found")
    return loadout


async def _slot_context(session: AsyncSession, loadout_id: int, slot: str) -> dict:
    """Shared by the full page and every HTMX fragment response, so a
    slot's card always renders identically regardless of which route built
    it - exactly one place that turns a LoadoutRequirement row (or lack of
    one) into display data."""
    result = await session.execute(
        select(LoadoutRequirement).where(
            LoadoutRequirement.loadout_id == loadout_id, LoadoutRequirement.slot == slot
        )
    )
    req = result.scalar_one_or_none()
    allowed_items = []
    if req is not None and req.allowed_item_ids:
        item_result = await session.execute(select(Item).where(Item.id.in_(req.allowed_item_ids)))
        by_id = {item.id: item for item in item_result.scalars().all()}
        allowed_items = [
            {"id": item_id, "name": by_id[item_id].name, "icon": item_icon_url(by_id[item_id].name)}
            for item_id in req.allowed_item_ids
            if item_id in by_id
        ]
    return {
        "slot": slot,
        "required": req.required if req is not None else False,
        "allowed_items": allowed_items,
    }


@router.get("/loadouts")
async def list_loadouts(
    request: Request,
    user: User = Depends(require_role(UserRole.STAFF)),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    result = await session.execute(select(Loadout).where(Loadout.fc_group_id == group.id))
    loadouts = result.scalars().all()
    context = template_context(request, user, loadouts=loadouts)
    return templates.TemplateResponse(request, "loadouts/list.html", context)


@router.get("/loadouts/{loadout_id}")
async def loadout_detail(
    loadout_id: int,
    request: Request,
    user: User = Depends(require_role(UserRole.STAFF)),
    session: AsyncSession = Depends(get_session),
):
    loadout = await _get_loadout(session, loadout_id)
    can_edit = user.role.value in ("staff", "admin")
    slots = []
    for slot in SLOT_ORDER:
        ctx = await _slot_context(session, loadout_id, slot)
        ctx["can_edit"] = can_edit
        slots.append(ctx)
    context = template_context(request, user, loadout=loadout, slots=slots)
    return templates.TemplateResponse(request, "loadouts/detail.html", context)


async def _slot_fragment_or_redirect(
    request: Request, session: AsyncSession, loadout_id: int, slot: str
):
    if request.headers.get("HX-Request") == "true":
        # Every caller of this helper already required UserRole.STAFF to
        # reach it, so the fragment can always show the editor controls -
        # no need to re-check role here.
        ctx = await _slot_context(session, loadout_id, slot)
        ctx.update(
            {
                "request": request,
                "loadout_id": loadout_id,
                "csrf_token": get_csrf_token(request),
                "can_edit": True,
            }
        )
        return templates.TemplateResponse(request, "loadouts/_slot.html", ctx)
    return RedirectResponse(url=f"/loadouts/{loadout_id}", status_code=303)


@router.post("/loadouts/{loadout_id}/slots/{slot}/required")
async def set_slot_required(
    loadout_id: int,
    slot: str,
    request: Request,
    required: bool = Form(False),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    slot = _check_slot(slot)
    await _get_loadout(session, loadout_id)
    result = await session.execute(
        select(LoadoutRequirement).where(
            LoadoutRequirement.loadout_id == loadout_id, LoadoutRequirement.slot == slot
        )
    )
    req = result.scalar_one_or_none()
    if req is None:
        if required:
            session.add(
                LoadoutRequirement(
                    loadout_id=loadout_id, slot=slot, required=True, allowed_item_ids=[]
                )
            )
    elif not required and not req.allowed_item_ids:
        # Dropping back to fully unrestricted - no allow-list and no longer
        # required means there's nothing left for this row to express.
        await session.delete(req)
    else:
        req.required = required
    await session.commit()
    return await _slot_fragment_or_redirect(request, session, loadout_id, slot)


@router.post("/loadouts/{loadout_id}/slots/{slot}/items")
async def add_slot_item(
    loadout_id: int,
    slot: str,
    request: Request,
    item_id: int = Form(...),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    slot = _check_slot(slot)
    await _get_loadout(session, loadout_id)
    item_result = await session.execute(select(Item).where(Item.id == item_id))
    if item_result.scalar_one_or_none() is None:
        raise HTTPException(status_code=404, detail="Unknown item")

    result = await session.execute(
        select(LoadoutRequirement).where(
            LoadoutRequirement.loadout_id == loadout_id, LoadoutRequirement.slot == slot
        )
    )
    req = result.scalar_one_or_none()
    if req is None:
        session.add(
            LoadoutRequirement(
                loadout_id=loadout_id, slot=slot, required=False, allowed_item_ids=[item_id]
            )
        )
    elif item_id not in (req.allowed_item_ids or []):
        req.allowed_item_ids = [*(req.allowed_item_ids or []), item_id]
    await session.commit()
    return await _slot_fragment_or_redirect(request, session, loadout_id, slot)


@router.post("/loadouts/{loadout_id}/slots/{slot}/items/{item_id}/delete")
async def remove_slot_item(
    loadout_id: int,
    slot: str,
    item_id: int,
    request: Request,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    slot = _check_slot(slot)
    await _get_loadout(session, loadout_id)
    result = await session.execute(
        select(LoadoutRequirement).where(
            LoadoutRequirement.loadout_id == loadout_id, LoadoutRequirement.slot == slot
        )
    )
    req = result.scalar_one_or_none()
    if req is not None and req.allowed_item_ids and item_id in req.allowed_item_ids:
        remaining = [i for i in req.allowed_item_ids if i != item_id]
        if not remaining and not req.required:
            await session.delete(req)
        else:
            req.allowed_item_ids = remaining
    await session.commit()
    return await _slot_fragment_or_redirect(request, session, loadout_id, slot)


@router.get("/loadouts/{loadout_id}/slots/{slot}/search")
async def search_slot_items(
    loadout_id: int,
    slot: str,
    request: Request,
    q: str = Query(default=""),
    user: User = Depends(require_role(UserRole.STAFF)),
    session: AsyncSession = Depends(get_session),
):
    slot = _check_slot(slot)
    await _get_loadout(session, loadout_id)
    results = []
    if q:
        item_result = await session.execute(select(Item).where(Item.name.ilike(f"%{q}%")).limit(20))
        results = [
            {"id": item.id, "name": item.name, "icon": item_icon_url(item.name)}
            for item in item_result.scalars().all()
        ]
    context = template_context(
        request, user, results=results, loadout_id=loadout_id, slot=slot, query=q
    )
    return templates.TemplateResponse(request, "loadouts/_item_search_results.html", context)
