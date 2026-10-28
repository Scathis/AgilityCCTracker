"""Permanently removing masses (e.g. test runs) and everything recorded
against them. Players, kits and settings are shared across masses and are
never touched."""

from dataclasses import dataclass, field

from sqlalchemy import delete, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.dd import DdCall, DdPresence
from app.models.death import Death
from app.models.event import Attendance, Event, EventParticipant, ParticipantStint
from app.models.lap import Lap
from app.models.loadout import LoadoutCheck

# Child tables in delete order (DD presence goes first, via its DD call).
# Label is what the confirm page calls each count.
_CHILDREN = [
    ("Attendance sessions", Attendance),
    ("Attending/declined answers", EventParticipant),
    ("Role stints", ParticipantStint),
    ("Laps", Lap),
    ("Deaths", Death),
    ("Gear checks", LoadoutCheck),
    ("DD calls", DdCall),
]


@dataclass
class EventSummary:
    event: Event
    counts: dict[str, int] = field(default_factory=dict)


async def deletable_events(session: AsyncSession, group_id: int, ids: list[int]) -> list[Event]:
    """The requested masses that belong to this group and have ended - a
    running mass can't be deleted (stop it first)."""
    if not ids:
        return []
    result = await session.execute(
        select(Event)
        .where(Event.id.in_(ids), Event.fc_group_id == group_id, Event.ended_at.is_not(None))
        .order_by(Event.started_at.desc())
    )
    return list(result.scalars().all())


async def summarise(session: AsyncSession, events: list[Event]) -> list[EventSummary]:
    summaries = []
    for event in events:
        summary = EventSummary(event)
        for label, model in _CHILDREN:
            count = await session.scalar(
                select(func.count()).select_from(model).where(model.event_id == event.id)
            )
            summary.counts[label] = count or 0
        summary.counts["DD tile timings"] = (
            await session.scalar(
                select(func.count())
                .select_from(DdPresence)
                .join(DdCall, DdCall.id == DdPresence.dd_call_id)
                .where(DdCall.event_id == event.id)
            )
            or 0
        )
        summaries.append(summary)
    return summaries


async def delete_events(session: AsyncSession, events: list[Event]) -> int:
    """Deletes the masses and all their data in one transaction."""
    ids = [e.id for e in events]
    if not ids:
        return 0
    call_ids = select(DdCall.id).where(DdCall.event_id.in_(ids)).scalar_subquery()
    await session.execute(delete(DdPresence).where(DdPresence.dd_call_id.in_(call_ids)))
    for _, model in _CHILDREN:
        await session.execute(delete(model).where(model.event_id.in_(ids)))
    await session.execute(delete(Event).where(Event.id.in_(ids)))
    await session.commit()
    return len(ids)
