"""POST /api/v1/ingest - BUILD_PLAN.md §6.4. Processes one IngestBatch in a
single transaction, in the 8 steps the spec lays out. The pure diff/merge
math lives in app/services/roster.py and app/services/dd.py; this module is
the DB-touching orchestration around those.
"""

import datetime as dt

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.deps import get_current_fc_group
from app.models.dd import DdCall, DdPresence
from app.models.death import Death
from app.models.event import Attendance, Event, EventParticipant
from app.models.fc_group import FcGroup
from app.models.lap import Lap
from app.models.loadout import LoadoutCheck, LoadoutCheckSource
from app.models.player import Player
from app.schemas.ingest import (
    DdSectionIn,
    IngestBatchIn,
    LoadoutCheckIn,
    RosterSnapshotIn,
    SelfStateIn,
)
from app.services.dd import PresenceMerge, find_matching_call, merge_broken_at, merge_presence
from app.services.gear_rules import running_anchor
from app.services.participation import (
    excluded_from_gear_and_dd,
    is_anti,
    is_attending,
    participants_by_rsn,
)
from app.services.roster import OpenSession, diff_roster
from app.util.time import from_epoch_ms

router = APIRouter()


@router.post("/ingest")
async def post_ingest(
    batch: IngestBatchIn,
    fc_group: FcGroup = Depends(get_current_fc_group),
    session: AsyncSession = Depends(get_session),
) -> dict:
    now = dt.datetime.utcnow()

    # Step 0: opt-in gate. Nothing from a reporter is stored unless they
    # answered "attending" for the currently active mass - no active mass,
    # no answer, or "no" all mean the whole batch is dropped untouched.
    active_event = await _active_event(session, fc_group.id)
    if active_event is None:
        return _ignored(now)
    participants = await participants_by_rsn(session, active_event.id)
    me = participants.get(batch.rsn)
    if not is_attending(me):
        return _ignored(now)
    is_anti_reporter = is_anti(me)

    # Step 1: upsert reporter.
    reporter = await _get_or_create_player(session, batch.rsn, now)
    reporter.is_plugin_user = True
    reporter.last_seen = now

    # Step 2: self-reported loadout_failed (which required slots are empty).
    # Antis are attendance-time only: no running-loadout checks.
    if batch.self_state is not None:
        if is_anti_reporter:
            batch.self_state.loadout_failed = None
        await _process_self(session, reporter, active_event, batch.self_state, now)

    # Step 3: laps (antis: skipped).
    if not is_anti_reporter:
        for lap in batch.laps:
            await _process_lap(
                session,
                reporter,
                fc_group.id,
                lap.lap_count,
                lap.completed_at,
                lap.lap_time_ms,
                lap.tickets_gained,
            )

    # Step 4: deaths.
    for death in batch.deaths:
        await _process_death(
            session, reporter, fc_group.id, death.died_at, death.world, death.wildy_level
        )

    # Step 5: roster.
    if batch.roster is not None:
        reporter_world = batch.self_state.world if batch.self_state is not None else None
        await _process_roster(session, fc_group.id, active_event, reporter_world, batch.roster, now)

    # Step 5b: course sightings keep an open attendance session alive.
    if batch.course_sightings:
        await _process_course_sightings(session, active_event, batch.course_sightings, now)

    # Step 6: observer-reported loadout checks.
    if fc_group.observer_enabled and batch.loadout_checks:
        await _process_loadout_checks(session, active_event, batch.loadout_checks, participants)

    # Step 7 + 8: DD merge, then laps-during-dd.
    if batch.dd is not None:
        await _process_dd(session, active_event, batch.dd, reporter.rsn, participants)

    await session.commit()
    return {"ok": True, "server_time": int(now.replace(tzinfo=dt.UTC).timestamp() * 1000)}


def _ignored(now: dt.datetime) -> dict:
    return {
        "ok": True,
        "ignored": True,
        "server_time": int(now.replace(tzinfo=dt.UTC).timestamp() * 1000),
    }


async def _get_or_create_player(session: AsyncSession, rsn: str, now: dt.datetime) -> Player:
    result = await session.execute(select(Player).where(Player.rsn == rsn))
    player = result.scalar_one_or_none()
    if player is None:
        player = Player(rsn=rsn, first_seen=now, last_seen=now, is_plugin_user=False)
        session.add(player)
        await session.flush()
    return player


async def _active_event(session: AsyncSession, fc_group_id: int) -> Event | None:
    result = await session.execute(
        select(Event)
        .where(Event.fc_group_id == fc_group_id, Event.ended_at.is_(None))
        .order_by(Event.started_at.desc())
    )
    return result.scalars().first()


async def _event_for_timestamp(
    session: AsyncSession, fc_group_id: int, ts: dt.datetime
) -> Event | None:
    """The event whose window contains `ts` - started_at <= ts <= (ended_at or "still running")."""
    result = await session.execute(
        select(Event).where(
            Event.fc_group_id == fc_group_id,
            Event.started_at <= ts,
            (Event.ended_at.is_(None)) | (Event.ended_at >= ts),
        )
    )
    return result.scalars().first()


async def _process_self(
    session: AsyncSession,
    player: Player,
    active_event: Event | None,
    self_state: SelfStateIn,
    now: dt.datetime,
) -> None:
    if active_event is not None and self_state.loadout_failed is not None:
        anchor = await running_anchor(session, active_event.fc_group_id)
        await _store_loadout_check_if_changed(
            session,
            player.id,
            active_event.id,
            anchor.id,
            self_state.loadout_failed,
            now,
            LoadoutCheckSource.SELF,
            reported_by_player_id=player.id,
        )


async def _store_loadout_check_if_changed(
    session: AsyncSession,
    player_id: int,
    event_id: int,
    loadout_id: int,
    failed: list[str],
    now: dt.datetime,
    source: LoadoutCheckSource,
    reported_by_player_id: int | None = None,
) -> None:
    result = await session.execute(
        select(LoadoutCheck)
        .where(
            LoadoutCheck.player_id == player_id,
            LoadoutCheck.event_id == event_id,
            LoadoutCheck.loadout_id == loadout_id,
        )
        .order_by(LoadoutCheck.checked_at.desc())
    )
    latest = result.scalars().first()
    if latest is not None and latest.failed_requirement_keys == failed:
        return
    session.add(
        LoadoutCheck(
            player_id=player_id,
            event_id=event_id,
            loadout_id=loadout_id,
            checked_at=now,
            failed_requirement_keys=failed,
            source=source,
            reported_by_player_id=reported_by_player_id,
        )
    )


async def _process_lap(
    session: AsyncSession,
    player: Player,
    fc_group_id: int,
    lap_count: int,
    completed_at_ms: int,
    lap_time_ms: int | None,
    tickets_gained: int | None,
) -> None:
    existing = await session.execute(
        select(Lap).where(Lap.player_id == player.id, Lap.lap_count == lap_count)
    )
    if existing.scalar_one_or_none() is not None:
        return
    completed_at = from_epoch_ms(completed_at_ms)
    event = await _event_for_timestamp(session, fc_group_id, completed_at)
    session.add(
        Lap(
            player_id=player.id,
            event_id=event.id if event else None,
            lap_count=lap_count,
            completed_at=completed_at,
            lap_time_ms=lap_time_ms,
            tickets_gained=tickets_gained,
        )
    )


async def _process_death(
    session: AsyncSession,
    player: Player,
    fc_group_id: int,
    died_at_ms: int,
    world: int,
    wildy_level: int,
) -> None:
    died_at = from_epoch_ms(died_at_ms)
    existing = await session.execute(
        select(Death).where(Death.player_id == player.id, Death.died_at == died_at)
    )
    if existing.scalar_one_or_none() is not None:
        return
    event = await _event_for_timestamp(session, fc_group_id, died_at)
    session.add(
        Death(
            player_id=player.id,
            event_id=event.id if event else None,
            died_at=died_at,
            world=world,
            wildy_level=wildy_level,
        )
    )


async def _process_roster(
    session: AsyncSession,
    fc_group_id: int,
    event: Event,
    reporter_world: int | None,
    roster: RosterSnapshotIn,
    now: dt.datetime,
) -> None:
    open_result = await session.execute(
        select(Attendance, Player)
        .join(Player, Attendance.player_id == Player.id)
        .where(Attendance.event_id == event.id, Attendance.left_at.is_(None))
    )
    rows = open_result.all()
    open_sessions = [OpenSession(player_id=p.id, rsn=p.rsn, world=a.world) for a, p in rows]
    attendance_by_player_id = {a.player_id: a for a, _ in rows}

    snapshot_rsns = {m.name for m in roster.members}
    diff = diff_roster(snapshot_rsns, open_sessions, reporter_world)

    snapshot_ts = from_epoch_ms(roster.ts)
    members_by_rsn = {m.name: m for m in roster.members}

    for rsn in diff.to_open:
        member = members_by_rsn[rsn]
        player = await _get_or_create_player(session, rsn, now)
        session.add(
            Attendance(
                event_id=event.id,
                player_id=player.id,
                joined_at=snapshot_ts,
                last_sighted_at=snapshot_ts,
                rank=member.rank,
                world=member.world,
            )
        )

    for player_id in diff.to_close:
        attendance_by_player_id[player_id].left_at = snapshot_ts

    # Members still present: keep world/rank current for future diffs.
    for session_info in open_sessions:
        member = members_by_rsn.get(session_info.rsn)
        if member is not None:
            attendance = attendance_by_player_id[session_info.player_id]
            attendance.world = member.world
            attendance.rank = member.rank


async def _process_course_sightings(
    session: AsyncSession, event: Event, sightings: list[str], now: dt.datetime
) -> None:
    """Keeps an already-open attendance session alive - never opens a new
    one (that stays the roster diff's job, so a sighting alone can't invent
    an attendance record for someone who was never actually a roster
    member). If a player's session has already been auto-closed by
    workers/attendance_timeout_worker.py, this is a no-op for them until the
    next roster snapshot reopens a fresh session - see BUILD_LOG.md."""
    result = await session.execute(
        select(Attendance)
        .join(Player, Attendance.player_id == Player.id)
        .where(
            Attendance.event_id == event.id,
            Attendance.left_at.is_(None),
            Player.rsn.in_(sightings),
        )
    )
    for attendance in result.scalars().all():
        attendance.last_sighted_at = now
        attendance.sighted_at_course = True


async def _process_loadout_checks(
    session: AsyncSession,
    event: Event,
    checks: list[LoadoutCheckIn],
    participants: dict[str, EventParticipant],
) -> None:
    anchor = await running_anchor(session, event.fc_group_id)
    for check in checks:
        # Antis (and plugin users who declined) are never gear-checked.
        if excluded_from_gear_and_dd(participants.get(check.rsn)):
            continue
        player_result = await session.execute(select(Player).where(Player.rsn == check.rsn))
        player = player_result.scalar_one_or_none()
        if player is None:
            continue

        attendance_result = await session.execute(
            select(Attendance).where(
                Attendance.event_id == event.id, Attendance.player_id == player.id
            )
        )
        if attendance_result.scalars().first() is None:
            continue

        await _store_loadout_check_if_changed(
            session,
            player.id,
            event.id,
            anchor.id,
            check.failed,
            from_epoch_ms(check.ts),
            LoadoutCheckSource.FC_SCAN,
        )


async def _process_dd(
    session: AsyncSession,
    event: Event,
    dd: DdSectionIn,
    reporter_rsn: str,
    participants: dict[str, EventParticipant],
) -> None:
    existing_calls_result = await session.execute(select(DdCall).where(DdCall.event_id == event.id))
    existing_calls = existing_calls_result.scalars().all()
    called_at_ms_by_id = {
        c.id: int(c.called_at.replace(tzinfo=dt.UTC).timestamp() * 1000) for c in existing_calls
    }

    match_id = find_matching_call(list(called_at_ms_by_id.items()), dd.called_at)
    if match_id is not None:
        dd_call = next(c for c in existing_calls if c.id == match_id)
        dd_call.reports += 1
        current_broken_ms = (
            int(dd_call.broken_at.replace(tzinfo=dt.UTC).timestamp() * 1000)
            if dd_call.broken_at
            else None
        )
        merged_broken_ms = merge_broken_at(current_broken_ms, dd.broken_at, staff_edited=False)
        dd_call.broken_at = (
            from_epoch_ms(merged_broken_ms) if merged_broken_ms is not None else None
        )
    else:
        dd_call = DdCall(
            event_id=event.id, called_at=from_epoch_ms(dd.called_at), broken_at=None, reports=1
        )
        session.add(dd_call)
        await session.flush()
        if dd.broken_at is not None:
            dd_call.broken_at = from_epoch_ms(dd.broken_at)

    for presence in dd.presence:
        if excluded_from_gear_and_dd(participants.get(presence.rsn)):
            continue
        player = await _get_or_create_player(session, presence.rsn, dt.datetime.utcnow())

        existing_result = await session.execute(
            select(DdPresence).where(
                DdPresence.dd_call_id == dd_call.id, DdPresence.player_id == player.id
            )
        )
        existing_row = existing_result.scalar_one_or_none()

        existing_merge = (
            PresenceMerge(
                arrived_after_ms=existing_row.arrived_after_ms,
                ms_on_tile=existing_row.ms_on_tile,
                left_before_break=existing_row.left_before_break,
                seen_running=existing_row.seen_running,
                observed_ms=existing_row.observed_ms,
                dd_loadout_failed=tuple(existing_row.dd_loadout_failed or []),
                sources=tuple(existing_row.sources or []),
            )
            if existing_row is not None
            else None
        )
        incoming = PresenceMerge(
            arrived_after_ms=presence.arrived_after_ms,
            ms_on_tile=presence.ms_on_tile,
            left_before_break=presence.left_before_break,
            seen_running=presence.seen_running,
            observed_ms=presence.observed_ms,
            dd_loadout_failed=tuple(presence.dd_failed),
        )
        merged = merge_presence(existing_merge, incoming, reporter=reporter_rsn)

        # Step 8: laps completed during the window also imply seen_running.
        window_end = dd_call.broken_at or dt.datetime.utcnow()
        laps_result = await session.execute(
            select(Lap).where(
                Lap.player_id == player.id,
                Lap.completed_at >= dd_call.called_at,
                Lap.completed_at <= window_end,
            )
        )
        laps_during_dd = len(laps_result.scalars().all())
        seen_running = merged.seen_running or laps_during_dd > 0

        if existing_row is not None:
            existing_row.arrived_after_ms = merged.arrived_after_ms
            existing_row.ms_on_tile = merged.ms_on_tile
            existing_row.left_before_break = merged.left_before_break
            existing_row.seen_running = seen_running
            existing_row.observed_ms = merged.observed_ms
            existing_row.dd_loadout_failed = list(merged.dd_loadout_failed)
            existing_row.sources = list(merged.sources)
            existing_row.laps_during_dd = laps_during_dd
        else:
            session.add(
                DdPresence(
                    dd_call_id=dd_call.id,
                    player_id=player.id,
                    arrived_after_ms=merged.arrived_after_ms,
                    ms_on_tile=merged.ms_on_tile,
                    left_before_break=merged.left_before_break,
                    seen_running=seen_running,
                    laps_during_dd=laps_during_dd,
                    observed_ms=merged.observed_ms,
                    dd_loadout_failed=list(merged.dd_loadout_failed),
                    sources=list(merged.sources),
                )
            )
