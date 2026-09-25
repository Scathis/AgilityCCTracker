import html as html_lib

from fastapi import APIRouter, Depends, Form, HTTPException, Query, Request
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.fc_group import FcGroup
from app.models.item import Item
from app.models.loadout import Loadout, LoadoutRequirement
from app.models.user import User, UserRole
from app.web.deps import (
    get_default_fc_group,
    require_role,
    require_user,
    template_context,
    verify_csrf,
)
from app.web.templating import templates

router = APIRouter()


@router.get("/loadouts")
async def list_loadouts(
    request: Request,
    user: User = Depends(require_user),
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
    user: User = Depends(require_user),
    session: AsyncSession = Depends(get_session),
):
    result = await session.execute(select(Loadout).where(Loadout.id == loadout_id))
    loadout = result.scalar_one_or_none()
    if loadout is None:
        raise HTTPException(status_code=404, detail="Loadout not found")
    req_result = await session.execute(
        select(LoadoutRequirement)
        .where(LoadoutRequirement.loadout_id == loadout_id)
        .order_by(LoadoutRequirement.sort)
    )
    requirements = req_result.scalars().all()
    context = template_context(request, user, loadout=loadout, requirements=requirements)
    return templates.TemplateResponse(request, "loadouts/detail.html", context)


@router.post("/loadouts/{loadout_id}/requirements")
async def add_requirement(
    loadout_id: int,
    req_key: str = Form(...),
    label: str = Form(...),
    slot: str = Form(...),
    check_type: str = Form(...),
    args: str = Form(""),
    sort: int = Form(0),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    arg_list = [a.strip() for a in args.split(",") if a.strip()]
    session.add(
        LoadoutRequirement(
            loadout_id=loadout_id,
            req_key=req_key,
            label=label,
            slot=slot.upper(),
            check_type=check_type.upper(),
            args=arg_list,
            when_json=None,
            sort=sort,
        )
    )
    await session.commit()
    return RedirectResponse(url=f"/loadouts/{loadout_id}", status_code=303)


@router.post("/loadouts/{loadout_id}/requirements/{req_id}/delete")
async def delete_requirement(
    loadout_id: int,
    req_id: int,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    result = await session.execute(
        select(LoadoutRequirement).where(LoadoutRequirement.id == req_id)
    )
    req = result.scalar_one_or_none()
    if req is not None:
        await session.delete(req)
        await session.commit()
    return RedirectResponse(url=f"/loadouts/{loadout_id}", status_code=303)


@router.get("/items/search", response_class=HTMLResponse)
async def search_items(
    q: str = Query(default=""),
    user: User = Depends(require_user),
    session: AsyncSession = Depends(get_session),
):
    if not q:
        return "<p>Type to search…</p>"
    result = await session.execute(select(Item).where(Item.name.ilike(f"%{q}%")).limit(20))
    items = result.scalars().all()
    if not items:
        return "<p>No matches.</p>"
    rows = "".join(f"<li>{html_lib.escape(item.name)} — id {item.id}</li>" for item in items)
    return f"<ul>{rows}</ul>"
