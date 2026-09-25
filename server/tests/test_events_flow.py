import datetime as dt

from sqlalchemy import select

from app.models.event import Attendance, Event
from app.models.loadout import Loadout, LoadoutCheck
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
            rank=0,
            world=330,
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
            rank=0,
            world=330,
        )
    )
    await db_session.commit()

    loadout_result = await db_session.execute(
        select(Loadout).where(Loadout.fc_group_id == default_group.id)
    )
    loadout = loadout_result.scalars().first()

    response = await client.post(
        f"/events/{event.id}/manual-gap",
        data={
            "player_id": player.id,
            "loadout_id": loadout.id,
            "failed": "run-helm, run-boots",
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
    assert any(c.failed_requirement_keys == ["run-helm", "run-boots"] for c in checks)
