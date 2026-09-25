import datetime as dt
import secrets

from sqlalchemy import select

from app.models.death import Death
from app.models.event import Attendance, Event
from app.models.lap import Lap
from app.models.loadout import LoadoutCheck
from app.models.player import Player
from app.seed import seed_default_loadouts
from app.util.time import to_epoch_ms


def _rsn() -> str:
    return "T" + secrets.token_hex(4)


def _headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


async def _get_player(db_session, rsn: str) -> Player | None:
    result = await db_session.execute(select(Player).where(Player.rsn == rsn))
    return result.scalar_one_or_none()


async def test_lap_ingest_is_idempotent(client, fc_group, db_session):
    group, token = fc_group
    rsn = _rsn()
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
    payload = {
        "rsn": _rsn(),
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
            rank=0,
            world=330,
        )
    )
    await db_session.commit()

    self_rsn = _rsn()
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
