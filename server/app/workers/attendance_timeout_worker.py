"""Auto-closes attendance sessions once nobody has sighted the player at the
course for too long - owner feedback from in-game testing: attendance was
tracking FC-channel membership (only closes when someone leaves the
channel), not actual course presence, so an AFK member's "time present"
just kept ticking up forever. Complements the sighting-heartbeat handling
in api/v1/ingest.py's _process_course_sightings().
"""

import asyncio
import datetime as dt
import logging

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import async_session_factory
from app.models.event import Attendance

logger = logging.getLogger(__name__)

TIMEOUT_S = 120
POLL_INTERVAL_S = 30


def should_close(
    last_sighted_at: dt.datetime, now: dt.datetime, timeout_s: int = TIMEOUT_S
) -> bool:
    """Pure - no `<=` edge case ambiguity: exactly at the timeout, stay open."""
    return (now - last_sighted_at).total_seconds() > timeout_s


async def run_once(session: AsyncSession) -> int:
    now = dt.datetime.utcnow()
    result = await session.execute(select(Attendance).where(Attendance.left_at.is_(None)))
    closed = 0
    for attendance in result.scalars().all():
        if should_close(attendance.last_sighted_at, now):
            # Closed at their last confirmed sighting, not "now" - "time
            # present" should reflect when they were actually last there.
            attendance.left_at = attendance.last_sighted_at
            closed += 1
    if closed:
        await session.commit()
    return closed


async def attendance_timeout_worker_loop() -> None:
    while True:
        try:
            async with async_session_factory() as session:
                closed = await run_once(session)
                if closed:
                    logger.info("attendance timeout worker closed %d stale session(s)", closed)
        except Exception:
            logger.exception("attendance timeout worker iteration failed")
        await asyncio.sleep(POLL_INTERVAL_S)
