import datetime as dt

from sqlalchemy import select

from app.models.event import Attendance, Event
from app.models.loadout import LoadoutCheck
from app.models.player import Player
from app.models.user import UserRole


async def _make_player(db_session, rsn: str) -> Player:
    player = Player(
        rsn=rsn,
        first_seen=dt.datetime.utcnow(),
        last_seen=dt.datetime.utcnow(),
        is_plugin_user=True,
    )
    db_session.add(player)
    await db_session.commit()
    await db_session.refresh(player)
    return player


async def test_full_event_lifecycle_and_every_tab_renders(
    client, make_user, login_as, default_group, no_active_event, db_session
):
    staff = await make_user(UserRole.STAFF)
    csrf = login_as(staff)

    start = await client.post(
        "/events",
        data={"name": "Tuesday mass", "notes": "test", "csrf_token": csrf},
        follow_redirects=False,
    )
    assert start.status_code == 303

    result = await db_session.execute(
        select(Event).where(Event.fc_group_id == default_group.id, Event.ended_at.is_(None))
    )
    event = result.scalar_one()

    player = await _make_player(db_session, "EvtFlowPlyr")
    db_session.add(
        Attendance(
            event_id=event.id,
            player_id=player.id,
            joined_at=dt.datetime.utcnow(),
            last_sighted_at=dt.datetime.utcnow(),
            sighted_at_course=True,
            rank=0,
        )
    )
    await db_session.commit()

    for tab in ("attendance", "laps", "dd", "deaths", "manual-gap"):
        response = await client.get(f"/events/{event.id}?tab={tab}")
        assert response.status_code == 200, f"tab={tab} failed: {response.text[:300]}"

    # While the event is active, attendance/laps/dd poll themselves via
    # HTMX - a normal page load includes the polling attribute and the
    # full page chrome; an HX-Request should get back just that tab's
    # fragment (no nav, no <h2> page title).
    for tab in ("attendance", "laps", "dd"):
        full_page = await client.get(f"/events/{event.id}?tab={tab}")
        assert f'hx-get="/events/{event.id}?tab={tab}"' in full_page.text
        assert "EvtFlowPlyr" in full_page.text or tab != "attendance"

        fragment = await client.get(
            f"/events/{event.id}?tab={tab}", headers={"HX-Request": "true"}
        )
        assert fragment.status_code == 200
        assert "<nav>" not in fragment.text
        assert "Tuesday mass" not in fragment.text

    stop = await client.post(
        f"/events/{event.id}/stop", data={"csrf_token": csrf}, follow_redirects=False
    )
    assert stop.status_code == 303

    # A finished event's tabs shouldn't poll anymore - nothing left to watch.
    stopped_page = await client.get(f"/events/{event.id}?tab=attendance")
    assert "hx-get=" not in stopped_page.text

    list_response = await client.get("/events")
    assert list_response.status_code == 200
    assert "Tuesday mass" in list_response.text


async def test_manual_gap_form_submission(
    client, make_user, login_as, default_group, no_active_event, db_session
):
    staff = await make_user(UserRole.STAFF)
    csrf = login_as(staff)

    await client.post("/events", data={"name": "Gap test event", "csrf_token": csrf})
    result = await db_session.execute(
        select(Event).where(Event.fc_group_id == default_group.id, Event.ended_at.is_(None))
    )
    event = result.scalar_one()

    player = await _make_player(db_session, "GapTestPlyr")
    db_session.add(
        Attendance(
            event_id=event.id,
            player_id=player.id,
            joined_at=dt.datetime.utcnow(),
            last_sighted_at=dt.datetime.utcnow(),
            rank=0,
        )
    )
    await db_session.commit()

    response = await client.post(
        f"/events/{event.id}/manual-gap",
        data={
            "player_id": player.id,
            "failed": ["BOOTS", "HEAD"],
            "notes": "seen without helm",
            "csrf_token": csrf,
        },
        follow_redirects=False,
    )
    assert response.status_code == 303

    check_result = await db_session.execute(
        select(LoadoutCheck).where(LoadoutCheck.player_id == player.id)
    )
    checks = check_result.scalars().all()
    # Stored in equipment-tab order, whatever order the boxes were ticked in.
    assert any(c.failed_requirement_keys == ["HEAD", "BOOTS"] for c in checks)


async def test_attendance_status_shows_present_vs_away(
    client, make_user, login_as, default_group, no_active_event, db_session
):
    staff = await make_user(UserRole.STAFF)
    csrf = login_as(staff)

    await client.post(
        "/events", data={"name": "Status Test Event", "notes": "", "csrf_token": csrf}
    )
    result = await db_session.execute(
        select(Event).where(Event.fc_group_id == default_group.id, Event.ended_at.is_(None))
    )
    event = result.scalar_one()

    present_player = await _make_player(db_session, "StillHere")
    away_player = await _make_player(db_session, "LeftEarly")
    db_session.add(
        Attendance(
            event_id=event.id,
            player_id=present_player.id,
            joined_at=dt.datetime.utcnow(),
            last_sighted_at=dt.datetime.utcnow(),
            left_at=None,
            sighted_at_course=True,
            rank=0,
        )
    )
    left_time = dt.datetime.utcnow() - dt.timedelta(minutes=10)
    db_session.add(
        Attendance(
            event_id=event.id,
            player_id=away_player.id,
            joined_at=left_time - dt.timedelta(minutes=5),
            last_sighted_at=left_time,
            left_at=left_time,
            sighted_at_course=True,
            rank=0,
        )
    )
    await db_session.commit()

    response = await client.get(f"/events/{event.id}?tab=attendance")
    assert response.status_code == 200
    text = response.text
    present_idx = text.index("StillHere")
    away_idx = text.index("LeftEarly")
    assert "🟢 Present" in text[present_idx:present_idx + 300]
    assert "⚪ Away" in text[away_idx:away_idx + 300]


async def test_attendance_lists_only_people_who_turned_up(
    client, make_user, login_as, default_group, no_active_event, db_session
):
    """Roster snapshots open a session for everyone in the FC - only people
    seen at the course (or who opted in / ran a lap) are listed."""
    staff = await make_user(UserRole.STAFF)
    csrf = login_as(staff)
    await client.post("/events", data={"name": "Turnout", "notes": "", "csrf_token": csrf})
    result = await db_session.execute(
        select(Event).where(Event.fc_group_id == default_group.id, Event.ended_at.is_(None))
    )
    event = result.scalar_one()

    came = await _make_player(db_session, "CameAlong")
    chat_only = await _make_player(db_session, "JustInChat")
    now = dt.datetime.utcnow()
    for player, sighted in ((came, True), (chat_only, False)):
        db_session.add(
            Attendance(
                event_id=event.id,
                player_id=player.id,
                joined_at=now,
                last_sighted_at=now,
                sighted_at_course=sighted,
                rank=0,
            )
        )
    await db_session.commit()

    text = (await client.get(f"/events/{event.id}?tab=attendance")).text
    assert "CameAlong" in text
    assert "JustInChat" not in text


async def test_mass_bank_is_flagged_to_plugins_only_during_a_running_mass(
    client, make_user, login_as, default_group, no_active_event, db_session
):
    staff = await make_user(UserRole.STAFF)
    csrf = login_as(staff)
    await client.post("/events", data={"name": "Bank test", "notes": "", "csrf_token": csrf})
    event = (
        await db_session.execute(
            select(Event).where(Event.fc_group_id == default_group.id, Event.ended_at.is_(None))
        )
    ).scalar_one()

    called = await client.post(
        f"/events/{event.id}/mass-bank", data={"csrf_token": csrf}, follow_redirects=False
    )
    assert called.status_code == 303
    await db_session.refresh(event)
    assert event.mass_bank_called_at is not None

    await client.post(f"/events/{event.id}/stop", data={"csrf_token": csrf})
    after_stop = await client.post(f"/events/{event.id}/mass-bank", data={"csrf_token": csrf})
    assert after_stop.status_code == 409


async def test_viewer_cannot_call_a_mass_bank(client, make_user, login_as, default_group):
    csrf = login_as(await make_user(UserRole.VIEWER))
    response = await client.post("/events/1/mass-bank", data={"csrf_token": csrf})
    assert response.status_code == 403
