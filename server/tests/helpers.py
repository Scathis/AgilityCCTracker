"""Shared test helpers (tests/ is on sys.path via pytest, so import as `helpers`)."""

import datetime as dt
import secrets

from sqlalchemy import select

from app.models.event import Event, EventParticipant, ParticipantRole, ParticipantStatus
from app.models.player import Player


def _rsn() -> str:
    return "T" + secrets.token_hex(4)


def _headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def _get_player(db_session, rsn: str) -> Player | None:
    result = await db_session.execute(select(Player).where(Player.rsn == rsn))
    return result.scalar_one_or_none()


async def _start_event(db_session, group) -> Event:
    event = Event(fc_group_id=group.id, name="E", started_at=dt.datetime.utcnow(), ended_at=None)
    db_session.add(event)
    await db_session.commit()
    await db_session.refresh(event)
    return event


async def _attend(
    db_session,
    event,
    rsn,
    role=ParticipantRole.RUNNER,
    status=ParticipantStatus.ATTENDING,
) -> Player:
    """Record the player's "mass started" answer directly (the respond endpoint
    itself is covered in test_participation.py)."""
    player = await _get_player(db_session, rsn)
    if player is None:
        now = dt.datetime.utcnow()
        player = Player(rsn=rsn, first_seen=now, last_seen=now, is_plugin_user=True)
        db_session.add(player)
        await db_session.flush()
    db_session.add(
        EventParticipant(
            event_id=event.id,
            player_id=player.id,
            status=status,
            role=role,
            responded_at=dt.datetime.utcnow(),
        )
    )
    await db_session.commit()
    return player
