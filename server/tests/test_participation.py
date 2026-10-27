import datetime as dt

from helpers import _attend, _get_player, _headers, _rsn, _start_event
from sqlalchemy import select

from app.models.dd import DdPresence
from app.models.death import Death
from app.models.event import (
    Attendance,
    Event,
    EventParticipant,
    ParticipantRole,
    ParticipantStatus,
    ParticipantStint,
)
from app.models.lap import Lap
from app.models.loadout import LoadoutCheck
from app.models.player import Player
from app.seed import seed_default_loadouts
from app.util.time import to_epoch_ms
from app.web.routes.events import _attendance_tab

DD_PRESENCE = {
    "arrived_after_ms": 1000,
    "ms_on_tile": 5000,
    "left_before_break": False,
    "seen_running": False,
    "observed_ms": 5000,
    "dd_failed": [],
}


async def _respond(client, token, rsn, event_id, attending=True, role="RUNNER"):
    return await client.post(
        "/api/v1/event/respond",
        json={"rsn": rsn, "event_id": event_id, "attending": attending, "role": role},
        headers=_headers(token),
    )


async def test_respond_records_answer_and_role_can_be_changed(client, fc_group, db_session):
    group, token = fc_group
    event = await _start_event(db_session, group)
    rsn = _rsn()

    assert (await _respond(client, token, rsn, event.id, role="ANTI")).status_code == 200
    assert (await _respond(client, token, rsn, event.id, role="runner")).status_code == 200

    player = await _get_player(db_session, rsn)
    assert player.is_plugin_user is True
    result = await db_session.execute(
        select(EventParticipant).where(EventParticipant.player_id == player.id)
    )
    rows = result.scalars().all()
    assert len(rows) == 1
    assert rows[0].status == ParticipantStatus.ATTENDING
    assert rows[0].role == ParticipantRole.RUNNER


async def test_respond_rejects_inactive_event_and_bad_role(client, fc_group, db_session):
    group, token = fc_group
    event = await _start_event(db_session, group)
    assert (await _respond(client, token, _rsn(), event.id, role="BOSS")).status_code == 422
    assert (await _respond(client, token, _rsn(), event.id + 9999)).status_code == 409

    event.ended_at = dt.datetime.utcnow()
    await db_session.commit()
    assert (await _respond(client, token, _rsn(), event.id)).status_code == 409


async def test_respond_requires_token(client, fc_group, db_session):
    group, _ = fc_group
    event = await _start_event(db_session, group)
    response = await client.post(
        "/api/v1/event/respond",
        json={"rsn": _rsn(), "event_id": event.id, "attending": True},
    )
    assert response.status_code == 401


async def test_status_lists_participants_for_active_mass_only(client, fc_group, db_session):
    group, token = fc_group
    event = await _start_event(db_session, group)
    yes, no = _rsn(), _rsn()
    await _respond(client, token, yes, event.id, role="ANTI")
    await _respond(client, token, no, event.id, attending=False)

    body = (await client.get("/api/v1/status", headers=_headers(token))).json()
    by_rsn = {p["rsn"]: p for p in body["participants"]}
    assert by_rsn[yes] == {"rsn": yes, "status": "ATTENDING", "role": "ANTI"}
    assert by_rsn[no]["status"] == "DECLINED"

    event.ended_at = dt.datetime.utcnow()
    await db_session.commit()
    body = (await client.get("/api/v1/status", headers=_headers(token))).json()
    assert body["participants"] == []


async def test_ingest_ignored_without_active_mass(client, fc_group, db_session):
    _, token = fc_group
    rsn = _rsn()
    payload = {
        "rsn": rsn,
        "laps": [{"lap_count": 1, "completed_at": 1_700_000_000_000}],
        "deaths": [{"died_at": 1_700_000_000_000, "world": 330, "wildy_level": 5}],
    }
    response = await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    assert response.status_code == 200
    assert response.json()["ignored"] is True
    assert await _get_player(db_session, rsn) is None


async def test_ingest_ignored_when_unanswered_or_declined(client, fc_group, db_session):
    group, token = fc_group
    event = await _start_event(db_session, group)
    unanswered, declined = _rsn(), _rsn()
    await _attend(db_session, event, declined, status=ParticipantStatus.DECLINED)

    for rsn in (unanswered, declined):
        payload = {"rsn": rsn, "laps": [{"lap_count": 1, "completed_at": 1_700_000_000_000}]}
        response = await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
        assert response.json()["ignored"] is True

    assert await _get_player(db_session, unanswered) is None
    declined_player = await _get_player(db_session, declined)
    laps = (await db_session.execute(select(Lap).where(Lap.player_id == declined_player.id))).all()
    assert laps == []


async def test_anti_reporter_is_attendance_time_only(client, fc_group, db_session):
    group, token = fc_group
    await seed_default_loadouts(db_session, group.id)
    event = await _start_event(db_session, group)
    anti = _rsn()
    await _attend(db_session, event, anti, role=ParticipantRole.ANTI)

    called_at = to_epoch_ms(dt.datetime.utcnow())
    payload = {
        "rsn": anti,
        "self": {"world": 330, "loadout_failed": ["run-helm"]},
        "laps": [{"lap_count": 1, "completed_at": 1_700_000_000_000}],
        "deaths": [{"died_at": 1_700_000_000_000, "world": 330, "wildy_level": 5}],
        "roster": {"ts": called_at, "members": [{"name": anti, "rank": 0, "world": 330}]},
        "dd": {"called_at": called_at, "presence": [{"rsn": anti, **DD_PRESENCE}]},
    }
    response = await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    assert response.status_code == 200
    assert "ignored" not in response.json()

    player = await _get_player(db_session, anti)
    for model in (Lap, LoadoutCheck, DdPresence):
        rows = (await db_session.execute(select(model).where(model.player_id == player.id))).all()
        assert rows == [], model
    # Attendance time and deaths are still recorded for antis.
    attendance = (
        await db_session.execute(select(Attendance).where(Attendance.player_id == player.id))
    ).scalars().all()
    assert len(attendance) == 1
    deaths = (await db_session.execute(select(Death).where(Death.player_id == player.id))).all()
    assert len(deaths) == 1


async def test_observer_reports_skip_antis_and_declined_but_not_unanswered(
    client, fc_group, db_session
):
    group, token = fc_group
    running, dd_loadout = await seed_default_loadouts(db_session, group.id)
    event = await _start_event(db_session, group)
    reporter, anti, declined, unanswered = _rsn(), _rsn(), _rsn(), _rsn()
    await _attend(db_session, event, reporter)
    anti_player = await _attend(db_session, event, anti, role=ParticipantRole.ANTI)
    declined_player = await _attend(db_session, event, declined, status=ParticipantStatus.DECLINED)

    now = dt.datetime.utcnow()
    unanswered_player = Player(rsn=unanswered, first_seen=now, last_seen=now)
    db_session.add(unanswered_player)
    await db_session.flush()
    for player in (anti_player, declined_player, unanswered_player):
        db_session.add(
            Attendance(
                event_id=event.id,
                player_id=player.id,
                joined_at=now,
                last_sighted_at=now,
                rank=0,
                world=330,
            )
        )
    await db_session.commit()

    called_at = to_epoch_ms(now)
    payload = {
        "rsn": reporter,
        "loadout_checks": [
            {"rsn": r, "loadout_id": running.id, "failed": ["run-helm"], "ts": called_at}
            for r in (anti, declined, unanswered)
        ],
        "dd": {
            "called_at": called_at,
            "presence": [{"rsn": r, **DD_PRESENCE} for r in (anti, declined, unanswered)],
        },
    }
    assert (
        await client.post("/api/v1/ingest", json=payload, headers=_headers(token))
    ).status_code == 200

    for player, expected in ((anti_player, 0), (declined_player, 0), (unanswered_player, 1)):
        checks = (
            await db_session.execute(
                select(LoadoutCheck).where(LoadoutCheck.player_id == player.id)
            )
        ).scalars().all()
        presences = (
            await db_session.execute(select(DdPresence).where(DdPresence.player_id == player.id))
        ).scalars().all()
        assert len(checks) == expected
        assert len(presences) == expected



async def test_switching_role_closes_the_old_stint_and_opens_a_new_one(
    client, fc_group, db_session
):
    group, token = fc_group
    event = await _start_event(db_session, group)
    rsn = _rsn()

    await _respond(client, token, rsn, event.id, role="ANTI")
    await _respond(client, token, rsn, event.id, role="ANTI")  # same role again: no new stint
    await _respond(client, token, rsn, event.id, role="RUNNER")
    await _respond(client, token, rsn, event.id, role="ANTI")

    player = await _get_player(db_session, rsn)
    stints = (
        (
            await db_session.execute(
                select(ParticipantStint)
                .where(ParticipantStint.player_id == player.id)
                .order_by(ParticipantStint.id)
            )
        )
        .scalars()
        .all()
    )
    assert [st.role for st in stints] == [
        ParticipantRole.ANTI,
        ParticipantRole.RUNNER,
        ParticipantRole.ANTI,
    ]
    assert stints[0].ended_at is not None
    assert stints[1].ended_at is not None
    assert stints[2].ended_at is None

    # Declining closes whatever is open and starts nothing.
    await _respond(client, token, rsn, event.id, attending=False)
    await db_session.refresh(stints[2])
    assert stints[2].ended_at is not None


async def test_attendance_tab_keeps_each_role_separate(db_session, default_group):
    now = dt.datetime.utcnow()
    event = Event(
        fc_group_id=default_group.id,
        name="Split",
        started_at=now - dt.timedelta(minutes=30),
        ended_at=None,
    )
    db_session.add(event)
    await db_session.flush()
    rsn = _rsn()
    player = Player(rsn=rsn, first_seen=now, last_seen=now, is_plugin_user=True)
    db_session.add(player)
    await db_session.flush()
    db_session.add(
        Attendance(
            event_id=event.id,
            player_id=player.id,
            joined_at=now - dt.timedelta(minutes=30),
            last_sighted_at=now,
            rank=0,
            world=330,
        )
    )
    switch = now - dt.timedelta(minutes=20)
    db_session.add_all(
        [
            ParticipantStint(
                event_id=event.id,
                player_id=player.id,
                role=ParticipantRole.ANTI,
                started_at=now - dt.timedelta(minutes=30),
                ended_at=switch,
            ),
            ParticipantStint(
                event_id=event.id,
                player_id=player.id,
                role=ParticipantRole.RUNNER,
                started_at=switch,
            ),
            Lap(
                player_id=player.id,
                event_id=event.id,
                lap_count=1,
                completed_at=now - dt.timedelta(minutes=5),
            ),
            Lap(
                player_id=player.id,
                event_id=event.id,
                lap_count=2,
                completed_at=now - dt.timedelta(minutes=4),
            ),
        ]
    )
    await db_session.commit()

    rows = (await _attendance_tab(db_session, default_group, event))["attendees"]
    mine = {r["role_label"]: r for r in rows if r["player"].id == player.id}
    assert set(mine) == {"Anti", "Runner"}
    assert 9 <= mine["Anti"]["seconds_present"] // 60 <= 10
    assert 19 <= mine["Runner"]["seconds_present"] // 60 <= 20
    assert mine["Anti"]["laps"] == 0
    assert mine["Runner"]["laps"] == 2
    assert mine["Anti"]["is_anti"] is True
