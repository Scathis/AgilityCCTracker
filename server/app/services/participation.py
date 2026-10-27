"""Per-mass opt-in + runner/anti role lookups shared by the status and ingest
routes. Rules (owner-decided 2026-10-27):

- A plugin user's data is only accepted for the active mass if they answered
  "attending" for it.
- Anti = attendance time only: no laps, no DD, no running-loadout checks.
- FC members who never answered (no plugin, e.g. mobile) are treated as
  runners, so organisers can still get their gear fixed.
"""

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.event import EventParticipant, ParticipantRole, ParticipantStatus
from app.models.player import Player


async def participants_by_rsn(
    session: AsyncSession, event_id: int
) -> dict[str, EventParticipant]:
    result = await session.execute(
        select(EventParticipant, Player.rsn)
        .join(Player, EventParticipant.player_id == Player.id)
        .where(EventParticipant.event_id == event_id)
    )
    return {rsn: participant for participant, rsn in result.all()}


def is_attending(participant: EventParticipant | None) -> bool:
    return participant is not None and participant.status == ParticipantStatus.ATTENDING


def is_anti(participant: EventParticipant | None) -> bool:
    return (
        participant is not None
        and is_attending(participant)
        and participant.role == ParticipantRole.ANTI
    )


def excluded_from_gear_and_dd(participant: EventParticipant | None) -> bool:
    """True for plugin users who declined or are antis - observers must not report on them."""
    if participant is None:
        return False
    return (
        participant.status == ParticipantStatus.DECLINED
        or participant.role == ParticipantRole.ANTI
    )
