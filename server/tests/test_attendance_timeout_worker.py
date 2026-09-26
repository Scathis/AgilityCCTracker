import datetime as dt
import secrets

from app.models.event import Attendance, Event
from app.models.player import Player
from app.workers.attendance_timeout_worker import TIMEOUT_S, run_once


def _rsn() -> str:
    return "W" + secrets.token_hex(4)


def _same_to_the_second(a: dt.datetime, b: dt.datetime) -> bool:
    """DATETIME columns here have no sub-second precision (a pre-existing
    gap against BUILD_PLAN.md §6.2's own "DATETIME(3)" convention, not
    introduced by this feature - see BUILD_LOG.md), so a value read back
    after a round-trip loses microseconds. Compare at the precision the
    column actually offers."""
    return abs((a - b).total_seconds()) < 1


async def _make_attendance(db_session, group, last_sighted_at: dt.datetime) -> Attendance:
    event = Event(fc_group_id=group.id, name="E", started_at=dt.datetime.utcnow(), ended_at=None)
    db_session.add(event)
    await db_session.commit()
    await db_session.refresh(event)

    player = Player(
        rsn=_rsn(),
        first_seen=dt.datetime.utcnow(),
        last_seen=dt.datetime.utcnow(),
    )
    db_session.add(player)
    await db_session.commit()
    await db_session.refresh(player)

    attendance = Attendance(
        event_id=event.id,
        player_id=player.id,
        joined_at=last_sighted_at,
        last_sighted_at=last_sighted_at,
        rank=0,
        world=330,
    )
    db_session.add(attendance)
    await db_session.commit()
    await db_session.refresh(attendance)
    return attendance


async def test_run_once_closes_stale_session(default_group, db_session):
    stale = dt.datetime.utcnow() - dt.timedelta(seconds=TIMEOUT_S + 30)
    attendance = await _make_attendance(db_session, default_group, stale)

    closed = await run_once(db_session)
    assert closed >= 1

    await db_session.refresh(attendance)
    assert _same_to_the_second(attendance.left_at, stale)


async def test_run_once_leaves_recently_sighted_session_open(default_group, db_session):
    recent = dt.datetime.utcnow() - dt.timedelta(seconds=5)
    attendance = await _make_attendance(db_session, default_group, recent)

    await run_once(db_session)

    await db_session.refresh(attendance)
    assert attendance.left_at is None


async def test_run_once_ignores_already_closed_sessions(default_group, db_session):
    stale = dt.datetime.utcnow() - dt.timedelta(seconds=TIMEOUT_S + 30)
    attendance = await _make_attendance(db_session, default_group, stale)
    already_left_at = dt.datetime.utcnow() - dt.timedelta(minutes=1)
    attendance.left_at = already_left_at
    await db_session.commit()

    await run_once(db_session)

    await db_session.refresh(attendance)
    # Unchanged - run_once only ever touches left_at IS NULL rows.
    assert _same_to_the_second(attendance.left_at, already_left_at)
