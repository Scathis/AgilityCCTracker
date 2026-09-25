import datetime as dt

from fastapi import APIRouter, Depends, Form, HTTPException, Request
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.event import Event
from app.models.fc_group import FcGroup
from app.models.ragger import Ragger, RaggerSighting, RaggerStatus
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


@router.get("/raggers")
async def list_raggers(
    request: Request,
    user: User = Depends(require_user),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    result = await session.execute(select(Ragger).order_by(Ragger.created_at.desc()))
    raggers = result.scalars().all()
    event_result = await session.execute(
        select(Event).where(Event.fc_group_id == group.id).order_by(Event.started_at.desc())
    )
    events = event_result.scalars().all()
    context = template_context(request, user, raggers=raggers, events=events)
    return templates.TemplateResponse(request, "raggers/list.html", context)


@router.post("/raggers")
async def add_ragger(
    rsn: str = Form(...),
    threat_notes: str = Form(""),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    existing = await session.execute(select(Ragger).where(Ragger.rsn == rsn))
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(status_code=400, detail="Ragger already logged")
    session.add(
        Ragger(
            rsn=rsn,
            status=RaggerStatus.ACTIVE,
            threat_notes=threat_notes or None,
            added_by_user_id=user.id,
            created_at=dt.datetime.utcnow(),
        )
    )
    await session.commit()
    return RedirectResponse(url="/raggers", status_code=303)


@router.post("/raggers/{ragger_id}/status")
async def update_ragger_status(
    ragger_id: int,
    status: str = Form(...),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    result = await session.execute(select(Ragger).where(Ragger.id == ragger_id))
    ragger = result.scalar_one_or_none()
    if ragger is None:
        raise HTTPException(status_code=404, detail="Ragger not found")
    ragger.status = RaggerStatus(status)
    await session.commit()
    return RedirectResponse(url="/raggers", status_code=303)


@router.post("/raggers/{ragger_id}/sightings")
async def add_sighting(
    ragger_id: int,
    event_id: str = Form(""),
    world: str = Form(""),
    gear_notes: str = Form(""),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    session.add(
        RaggerSighting(
            ragger_id=ragger_id,
            event_id=int(event_id) if event_id else None,
            seen_at=dt.datetime.utcnow(),
            world=int(world) if world else None,
            gear_notes=gear_notes or None,
            reported_by_user_id=user.id,
        )
    )
    await session.commit()
    return RedirectResponse(url="/raggers", status_code=303)


@router.post("/raggers/{ragger_id}/refresh")
async def refresh_ragger(
    ragger_id: int,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    """Clears hiscores_updated_at so the background worker picks this ragger up
    on its next minute-tick, rather than duplicating the fetch logic here."""
    result = await session.execute(select(Ragger).where(Ragger.id == ragger_id))
    ragger = result.scalar_one_or_none()
    if ragger is not None:
        ragger.hiscores_updated_at = None
        await session.commit()
    return RedirectResponse(url="/raggers", status_code=303)
