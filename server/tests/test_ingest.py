import datetime as dt

from helpers import _attend, _get_player, _headers, _rsn, _start_event
from sqlalchemy import select

from app.models.dd import DdPresence
from app.models.death import Death
from app.models.event import (
    Attendance,
    Event,
)
from app.models.lap import Lap
from app.models.loadout import LoadoutCheck
from app.models.player import Player
from app.seed import seed_default_loadouts
from app.util.time import to_epoch_ms


async def test_lap_ingest_is_idempotent(client, fc_group, db_session):
    group, token = fc_group
    rsn = _rsn()
    await _attend(db_session, await _start_event(db_session, group), rsn)
    payload = {"rsn": rsn, "laps": [{"lap_count": 1, "completed_at": 1_700_000_000_000}]}

    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200
    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200

    player = await _get_player(db_session, rsn)
    result = await db_session.execute(select(Lap).where(Lap.player_id == player.id))
    assert len(result.scalars().all()) == 1


async def test_death_ingest_is_idempotent(client, fc_group, db_session):
    group, token = fc_group
    rsn = _rsn()
    await _attend(db_session, await _start_event(db_session, group), rsn)
    payload = {
        "rsn": rsn,
        "deaths": [{"died_at": 1_700_000_000_000, "world": 330, "wildy_level": 12}],
    }

    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200
    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200

    player = await _get_player(db_session, rsn)
    result = await db_session.execute(select(Death).where(Death.player_id == player.id))
    assert len(result.scalars().all()) == 1


async def test_lap_resolves_to_the_event_whose_window_contains_it(client, fc_group, db_session):
    group, token = fc_group
    t0 = dt.datetime(2026, 1, 1, 0, 0, 0)
    event_a = Event(
        fc_group_id=group.id, name="A", started_at=t0, ended_at=t0 + dt.timedelta(hours=1)
    )
    event_b = Event(
        fc_group_id=group.id, name="B", started_at=t0 + dt.timedelta(hours=2), ended_at=None
    )
    db_session.add_all([event_a, event_b])
    await db_session.commit()
    await db_session.refresh(event_a)
    await db_session.refresh(event_b)

    rsn = _rsn()
    await _attend(db_session, event_b, rsn)
    in_a = to_epoch_ms(t0 + dt.timedelta(minutes=30))
    in_b = to_epoch_ms(t0 + dt.timedelta(hours=3))
    in_gap = to_epoch_ms(t0 + dt.timedelta(hours=1, minutes=30))

    payload = {
        "rsn": rsn,
        "laps": [
            {"lap_count": 1, "completed_at": in_a},
            {"lap_count": 2, "completed_at": in_b},
            {"lap_count": 3, "completed_at": in_gap},
        ],
    }
    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200

    player = await _get_player(db_session, rsn)
    result = await db_session.execute(
        select(Lap).where(Lap.player_id == player.id).order_by(Lap.lap_count)
    )
    laps = result.scalars().all()
    assert laps[0].event_id == event_a.id
    assert laps[1].event_id == event_b.id
    assert laps[2].event_id is None


async def test_self_loadout_check_only_stores_on_change(client, fc_group, db_session):
    group, token = fc_group
    await seed_default_loadouts(db_session, group.id)
    event = Event(fc_group_id=group.id, name="E", started_at=dt.datetime.utcnow(), ended_at=None)
    db_session.add(event)
    await db_session.commit()

    rsn = _rsn()
    await _attend(db_session, event, rsn)
    payload = {"rsn": rsn, "self": {"loadout_failed": ["run-helm"]}}
    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200
    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200

    player = await _get_player(db_session, rsn)
    result = await db_session.execute(
        select(LoadoutCheck).where(LoadoutCheck.player_id == player.id)
    )
    assert len(result.scalars().all()) == 1

    payload2 = {"rsn": rsn, "self": {"loadout_failed": ["run-helm", "run-legs"]}}
    assert (
        await client.post("/api/v1/ingest", json=payload2, headers=_headers(token))
    ).status_code == 200
    result = await db_session.execute(
        select(LoadoutCheck).where(LoadoutCheck.player_id == player.id)
    )
    assert len(result.scalars().all()) == 2


async def test_fc_scan_loadout_check_needs_an_attendance_row(client, fc_group, db_session):
    group, token = fc_group
    running, _dd = await seed_default_loadouts(db_session, group.id)
    event = Event(fc_group_id=group.id, name="E", started_at=dt.datetime.utcnow(), ended_at=None)
    db_session.add(event)
    await db_session.commit()
    await db_session.refresh(event)

    rsn = _rsn()
    reporter_rsn = _rsn()
    await _attend(db_session, event, reporter_rsn)
    payload = {
        "rsn": reporter_rsn,
        "loadout_checks": [
            {"rsn": rsn, "loadout_id": running.id, "failed": ["run-helm"], "ts": 1_700_000_000_000}
        ],
    }
    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200

    # No player/attendance exists yet for `rsn` - the check must be dropped, not stored.
    player = await _get_player(db_session, rsn)
    assert player is None

    # Create the player + an attendance row for this event, then resubmit.
    new_player = Player(
        rsn=rsn,
        first_seen=dt.datetime.utcnow(),
        last_seen=dt.datetime.utcnow(),
        is_plugin_user=False,
    )
    db_session.add(new_player)
    await db_session.flush()
    db_session.add(
        Attendance(
            event_id=event.id,
            player_id=new_player.id,
            joined_at=dt.datetime.utcnow(),
            last_sighted_at=dt.datetime.utcnow(),
            rank=0,
            world=330,
        )
    )
    await db_session.commit()

    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200
    result = await db_session.execute(
        select(LoadoutCheck).where(LoadoutCheck.player_id == new_player.id)
    )
    assert len(result.scalars().all()) == 1


async def test_observer_disabled_drops_fc_scan_but_not_self(client, fc_group, db_session):
    group, token = fc_group
    group.observer_enabled = False
    await db_session.commit()

    running, _dd = await seed_default_loadouts(db_session, group.id)
    event = Event(fc_group_id=group.id, name="E", started_at=dt.datetime.utcnow(), ended_at=None)
    db_session.add(event)
    await db_session.commit()
    await db_session.refresh(event)

    scanned_rsn = _rsn()
    scanned_player = Player(
        rsn=scanned_rsn,
        first_seen=dt.datetime.utcnow(),
        last_seen=dt.datetime.utcnow(),
        is_plugin_user=False,
    )
    db_session.add(scanned_player)
    await db_session.flush()
    db_session.add(
        Attendance(
            event_id=event.id,
            player_id=scanned_player.id,
            joined_at=dt.datetime.utcnow(),
            last_sighted_at=dt.datetime.utcnow(),
            rank=0,
            world=330,
        )
    )
    await db_session.commit()

    self_rsn = _rsn()
    await _attend(db_session, event, self_rsn)
    payload = {
        "rsn": self_rsn,
        "self": {"loadout_failed": ["run-helm"]},
        "loadout_checks": [
            {
                "rsn": scanned_rsn,
                "loadout_id": running.id,
                "failed": ["run-boots"],
                "ts": 1_700_000_000_000,
            }
        ],
    }
    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200

    fc_scan_result = await db_session.execute(
        select(LoadoutCheck).where(LoadoutCheck.player_id == scanned_player.id)
    )
    assert fc_scan_result.scalars().all() == []

    self_player = await _get_player(db_session, self_rsn)
    self_result = await db_session.execute(
        select(LoadoutCheck).where(LoadoutCheck.player_id == self_player.id)
    )
    assert len(self_result.scalars().all()) == 1


async def test_dd_presence_merges_multiple_reporters_without_duplicating(
    client, fc_group, db_session
):
    """Real scenario from in-game testing: several plugin users all
    observing the same DD tile. Each of their reports must merge into one
    DdPresence row per (call, observed player) - never one row per
    reporter - and `sources` must record who actually reported it, not the
    observed player's own name (a real bug found and fixed alongside this
    test: the reporter's rsn was never threaded into merge_presence())."""
    group, token = fc_group
    event = Event(fc_group_id=group.id, name="E", started_at=dt.datetime.utcnow(), ended_at=None)
    db_session.add(event)
    await db_session.commit()

    watched_rsn = _rsn()
    reporter_a = _rsn()
    reporter_b = _rsn()
    await _attend(db_session, event, reporter_a)
    await _attend(db_session, event, reporter_b)
    called_at = to_epoch_ms(dt.datetime.utcnow())

    payload_a = {
        "rsn": reporter_a,
        "dd": {
            "called_at": called_at,
            "presence": [
                {
                    "rsn": watched_rsn,
                    "arrived_after_ms": 5000,
                    "ms_on_tile": 20000,
                    "left_before_break": False,
                    "seen_running": False,
                    "observed_ms": 20000,
                    "dd_failed": [],
                }
            ],
        },
    }
    payload_b = {
        "rsn": reporter_b,
        "dd": {
            "called_at": called_at,
            "presence": [
                {
                    "rsn": watched_rsn,
                    "arrived_after_ms": 3000,
                    "ms_on_tile": 45000,
                    "left_before_break": False,
                    "seen_running": False,
                    "observed_ms": 45000,
                    "dd_failed": [],
                }
            ],
        },
    }
    assert (
        await client.post("/api/v1/ingest", json=payload_a, headers=_headers(token))
    ).status_code == 200
    assert (
        await client.post("/api/v1/ingest", json=payload_b, headers=_headers(token))
    ).status_code == 200

    watched_player = await _get_player(db_session, watched_rsn)
    result = await db_session.execute(
        select(DdPresence).where(DdPresence.player_id == watched_player.id)
    )
    rows = result.scalars().all()

    # One merged row, not two - the whole point of the merge.
    assert len(rows) == 1
    row = rows[0]
    assert row.ms_on_tile == 45000  # max() of the two reports
    assert row.arrived_after_ms == 3000  # min() of the two reports
    assert set(row.sources) == {reporter_a, reporter_b}  # who reported it
    assert watched_rsn not in row.sources  # not the observed player's own name


async def test_course_sighting_keeps_open_attendance_alive(client, fc_group, db_session):
    group, token = fc_group
    event = Event(fc_group_id=group.id, name="E", started_at=dt.datetime.utcnow(), ended_at=None)
    db_session.add(event)
    await db_session.commit()
    await db_session.refresh(event)

    watched_rsn = _rsn()
    watched_player = Player(
        rsn=watched_rsn, first_seen=dt.datetime.utcnow(), last_seen=dt.datetime.utcnow()
    )
    db_session.add(watched_player)
    await db_session.commit()
    await db_session.refresh(watched_player)

    stale_time = dt.datetime.utcnow() - dt.timedelta(minutes=5)
    db_session.add(
        Attendance(
            event_id=event.id,
            player_id=watched_player.id,
            joined_at=stale_time,
            last_sighted_at=stale_time,
            rank=0,
            world=330,
        )
    )
    await db_session.commit()

    reporter_rsn = _rsn()
    await _attend(db_session, event, reporter_rsn)
    payload = {"rsn": reporter_rsn, "course_sightings": [watched_rsn]}
    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200

    result = await db_session.execute(
        select(Attendance).where(Attendance.player_id == watched_player.id)
    )
    attendance = result.scalar_one()
    assert attendance.left_at is None
    assert attendance.last_sighted_at > stale_time
    assert attendance.sighted_at_course is True


async def test_course_sighting_does_not_open_a_new_attendance_record(client, fc_group, db_session):
    """A sighting alone can't invent attendance for someone with no
    existing open session - that stays the roster diff's job."""
    group, token = fc_group
    event = Event(fc_group_id=group.id, name="E", started_at=dt.datetime.utcnow(), ended_at=None)
    db_session.add(event)
    await db_session.commit()

    never_attended_rsn = _rsn()
    reporter_rsn = _rsn()
    await _attend(db_session, event, reporter_rsn)
    payload = {"rsn": reporter_rsn, "course_sightings": [never_attended_rsn]}
    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200

    result = await db_session.execute(select(Attendance).where(Attendance.event_id == event.id))
    assert result.scalars().all() == []


async def test_oversized_rsn_rejected_with_422(client, fc_group):
    _, token = fc_group
    payload = {"rsn": "ThisNameIsWayTooLong"}
    response = await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    assert response.status_code == 422


async def test_implausibly_fast_lap_time_rejected_with_422(client, fc_group):
    _, token = fc_group
    payload = {
        "rsn": _rsn(),
        "laps": [{"lap_count": 1, "completed_at": 1_700_000_000_000, "lap_time_ms": 500}],
    }
    response = await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    assert response.status_code == 422


async def test_a_real_lap_time_is_accepted(client, fc_group, db_session):
    group, token = fc_group
    rsn = _rsn()
    await _attend(db_session, await _start_event(db_session, group), rsn)
    payload = {
        "rsn": rsn,
        "laps": [{"lap_count": 1, "completed_at": 1_700_000_000_000, "lap_time_ms": 45_000}],
    }
    response = await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    assert response.status_code == 200
    player = await _get_player(db_session, rsn)
    result = await db_session.execute(select(Lap).where(Lap.player_id == player.id))
    assert len(result.scalars().all()) == 1


async def test_more_than_one_ticket_per_lap_rejected_with_422(client, fc_group):
    _, token = fc_group
    payload = {
        "rsn": _rsn(),
        "laps": [{"lap_count": 1, "completed_at": 1_700_000_000_000, "tickets_gained": 5}],
    }
    response = await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    assert response.status_code == 422
