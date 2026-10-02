from fastapi import APIRouter, Depends, HTTPException, Query, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.dd import DdPresence
from app.models.event import Attendance, Event
from app.models.lap import Lap
from app.models.loadout import LoadoutCheck
from app.models.player import Player, PlayerSnapshot
from app.models.user import User, UserRole
from app.web.deps import require_role, template_context
from app.web.templating import templates

router = APIRouter()


@router.get("/players")
async def list_players(
    request: Request,
    q: str = Query(default=""),
    user: User = Depends(require_role(UserRole.STAFF)),
    session: AsyncSession = Depends(get_session),
):
    query = select(Player)
    if q:
        query = query.where(Player.rsn.ilike(f"%{q}%"))
    result = await session.execute(query.order_by(Player.rsn).limit(200))
    players = result.scalars().all()
    context = template_context(request, user, players=players, q=q)
    return templates.TemplateResponse(request, "players/list.html", context)


@router.get("/players/{player_id}")
async def player_profile(
    player_id: int,
    request: Request,
    user: User = Depends(require_role(UserRole.STAFF)),
    session: AsyncSession = Depends(get_session),
):
    result = await session.execute(select(Player).where(Player.id == player_id))
    player = result.scalar_one_or_none()
    if player is None:
        raise HTTPException(status_code=404, detail="Player not found")

    latest_snapshot = None
    if player.latest_snapshot_id is not None:
        snap_result = await session.execute(
            select(PlayerSnapshot).where(PlayerSnapshot.id == player.latest_snapshot_id)
        )
        latest_snapshot = snap_result.scalar_one_or_none()

    lap_result = await session.execute(
        select(Lap).where(Lap.player_id == player_id).order_by(Lap.completed_at.desc()).limit(50)
    )
    laps = lap_result.scalars().all()

    attendance_result = await session.execute(
        select(Attendance, Event)
        .join(Event, Attendance.event_id == Event.id)
        .where(Attendance.player_id == player_id)
    )
    attendance = attendance_result.all()

    loadout_result = await session.execute(
        select(LoadoutCheck)
        .where(LoadoutCheck.player_id == player_id)
        .order_by(LoadoutCheck.checked_at.desc())
        .limit(50)
    )
    loadout_history = loadout_result.scalars().all()

    dd_result = await session.execute(select(DdPresence).where(DdPresence.player_id == player_id))
    dd_total_ms = sum(p.ms_on_tile for p in dd_result.scalars().all())

    context = template_context(
        request,
        user,
        player=player,
        snapshot=latest_snapshot,
        laps=laps,
        attendance=attendance,
        loadout_history=loadout_history,
        dd_total_ms=dd_total_ms,
    )
    return templates.TemplateResponse(request, "players/profile.html", context)
