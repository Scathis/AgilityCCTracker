"""POST /api/v1/event/respond - a plugin user's answer to the "mass started,
are you attending?" prompt. Idempotent upsert; the role can be changed by
answering again while the mass is still active."""

import datetime as dt

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.deps import get_current_fc_group
from app.models.event import (
    Event,
    EventParticipant,
    ParticipantRole,
    ParticipantStatus,
    ParticipantStint,
)
from app.models.fc_group import FcGroup
from app.models.player import Player
from app.schemas.ingest import RespondIn

router = APIRouter()


@router.post("/event/respond")
async def respond(
    body: RespondIn,
    fc_group: FcGroup = Depends(get_current_fc_group),
    session: AsyncSession = Depends(get_session),
) -> dict:
    try:
        role = ParticipantRole(body.role.lower())
    except ValueError as exc:
        raise HTTPException(status_code=422, detail="role must be RUNNER or ANTI") from exc

    event = (
        await session.execute(
            select(Event).where(
                Event.id == body.event_id,
                Event.fc_group_id == fc_group.id,
                Event.ended_at.is_(None),
            )
        )
    ).scalar_one_or_none()
    if event is None:
        raise HTTPException(status_code=409, detail="That mass is not active")

    now = dt.datetime.utcnow()
    player = (
        await session.execute(select(Player).where(Player.rsn == body.rsn))
    ).scalar_one_or_none()
    if player is None:
        player = Player(rsn=body.rsn, first_seen=now, last_seen=now, is_plugin_user=True)
        session.add(player)
        await session.flush()
    player.is_plugin_user = True
    player.last_seen = now

    status = ParticipantStatus.ATTENDING if body.attending else ParticipantStatus.DECLINED
    existing = (
        await session.execute(
            select(EventParticipant).where(
                EventParticipant.event_id == event.id, EventParticipant.player_id == player.id
            )
        )
    ).scalar_one_or_none()
    if existing is None:
        session.add(
            EventParticipant(
                event_id=event.id,
                player_id=player.id,
                status=status,
                role=role,
                responded_at=now,
            )
        )
    else:
        existing.status = status
        existing.role = role
        existing.responded_at = now

    # Stint log: a role change (or leaving) closes the open stint; a new role opens a new one.
    open_stint = (
        await session.execute(
            select(ParticipantStint).where(
                ParticipantStint.event_id == event.id,
                ParticipantStint.player_id == player.id,
                ParticipantStint.ended_at.is_(None),
            )
        )
    ).scalar_one_or_none()
    if open_stint is not None and not (body.attending and open_stint.role == role):
        open_stint.ended_at = now
        open_stint = None
    if body.attending and open_stint is None:
        session.add(
            ParticipantStint(
                event_id=event.id, player_id=player.id, role=role, started_at=now
            )
        )

    await session.commit()
    return {"ok": True}
