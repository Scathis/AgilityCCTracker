import csv
import io

from fastapi import APIRouter, Depends, Query, Request
from fastapi.responses import Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.dd import DdCall, DdPresence
from app.models.event import Event
from app.models.fc_group import FcGroup
from app.models.player import Player
from app.models.user import User
from app.web.deps import get_default_fc_group, require_user, template_context
from app.web.templating import templates

router = APIRouter()


async def _leaderboard_rows(
    session: AsyncSession, group_id: int, event_id: int | None
) -> list[tuple[str, int]]:
    query = (
        select(Player.rsn, DdPresence.ms_on_tile)
        .join(DdPresence, DdPresence.player_id == Player.id)
        .join(DdCall, DdCall.id == DdPresence.dd_call_id)
        .join(Event, Event.id == DdCall.event_id)
        .where(Event.fc_group_id == group_id)
    )
    if event_id is not None:
        query = query.where(Event.id == event_id)
    rows = (await session.execute(query)).all()
    totals: dict[str, int] = {}
    for rsn, ms in rows:
        totals[rsn] = totals.get(rsn, 0) + ms
    return sorted(totals.items(), key=lambda kv: kv[1], reverse=True)


@router.get("/dd/leaderboard")
async def dd_leaderboard(
    request: Request,
    event_id: int | None = Query(default=None),
    user: User = Depends(require_user),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    leaderboard = await _leaderboard_rows(session, group.id, event_id)
    context = template_context(request, user, leaderboard=leaderboard, top3=leaderboard[:3])
    return templates.TemplateResponse(request, "dd/leaderboard.html", context)


@router.get("/dd/leaderboard.csv")
async def dd_leaderboard_csv(
    event_id: int | None = Query(default=None),
    user: User = Depends(require_user),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    leaderboard = await _leaderboard_rows(session, group.id, event_id)
    buf = io.StringIO()
    writer = csv.writer(buf)
    writer.writerow(["rsn", "ms_on_tile"])
    writer.writerows(leaderboard)
    return Response(
        content=buf.getvalue(),
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=dd_leaderboard.csv"},
    )
