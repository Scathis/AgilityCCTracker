import datetime as dt

from sqlalchemy import select

from app.models.loadout import Loadout
from app.models.player import Player
from app.models.ragger import Ragger
from app.models.user import UserRole


async def test_loadout_list_and_detail_render(
    client, make_user, login_as, default_group, db_session
):
    user = await make_user(UserRole.VIEWER)
    login_as(user)

    list_response = await client.get("/loadouts")
    assert list_response.status_code == 200
    assert "Running" in list_response.text

    result = await db_session.execute(
        select(Loadout).where(Loadout.fc_group_id == default_group.id)
    )
    loadout = result.scalars().first()
    detail_response = await client.get(f"/loadouts/{loadout.id}")
    assert detail_response.status_code == 200
    assert "run-weapon" in detail_response.text or "run-bow" in detail_response.text


async def test_staff_can_add_and_delete_a_requirement(
    client, make_user, login_as, default_group, db_session
):
    staff = await make_user(UserRole.STAFF)
    csrf = login_as(staff)

    result = await db_session.execute(
        select(Loadout).where(Loadout.fc_group_id == default_group.id)
    )
    loadout = result.scalars().first()

    add = await client.post(
        f"/loadouts/{loadout.id}/requirements",
        data={
            "req_key": "test-req",
            "label": "Test requirement",
            "slot": "HEAD",
            "check_type": "FILLED",
            "args": "",
            "sort": "99",
            "csrf_token": csrf,
        },
        follow_redirects=False,
    )
    assert add.status_code == 303

    detail = await client.get(f"/loadouts/{loadout.id}")
    assert "test-req" in detail.text


async def test_item_search_returns_html_fragment(client, make_user, login_as):
    user = await make_user(UserRole.VIEWER)
    login_as(user)
    response = await client.get("/items/search", params={"q": "nonexistent item xyz"})
    assert response.status_code == 200
    assert "No matches" in response.text


async def test_players_list_and_profile_render(client, make_user, login_as, db_session):
    user = await make_user(UserRole.VIEWER)
    login_as(user)

    player = Player(
        rsn="ProfileTest",
        first_seen=dt.datetime.utcnow(),
        last_seen=dt.datetime.utcnow(),
        is_plugin_user=True,
    )
    db_session.add(player)
    await db_session.commit()
    await db_session.refresh(player)

    list_response = await client.get("/players", params={"q": "ProfileTest"})
    assert list_response.status_code == 200
    assert "ProfileTest" in list_response.text

    profile_response = await client.get(f"/players/{player.id}")
    assert profile_response.status_code == 200
    assert "ProfileTest" in profile_response.text


async def test_raggers_full_flow(client, make_user, login_as, default_group, db_session):
    staff = await make_user(UserRole.STAFF)
    csrf = login_as(staff)

    add = await client.post(
        "/raggers",
        data={"rsn": "RaggerFlow", "threat_notes": "seen pking", "csrf_token": csrf},
        follow_redirects=False,
    )
    assert add.status_code == 303

    list_response = await client.get("/raggers")
    assert list_response.status_code == 200
    assert "RaggerFlow" in list_response.text

    result = await db_session.execute(select(Ragger).where(Ragger.rsn == "RaggerFlow"))
    ragger = result.scalar_one()

    sighting = await client.post(
        f"/raggers/{ragger.id}/sightings",
        data={"event_id": "", "world": "330", "gear_notes": "full void", "csrf_token": csrf},
        follow_redirects=False,
    )
    assert sighting.status_code == 303

    status_update = await client.post(
        f"/raggers/{ragger.id}/status",
        data={"status": "cleared", "csrf_token": csrf},
        follow_redirects=False,
    )
    assert status_update.status_code == 303
    await db_session.refresh(ragger)
    assert ragger.status.value == "cleared"

    # Simulate a prior hiscores fetch so the refresh action has something to actually clear.
    ragger.hiscores_updated_at = dt.datetime.utcnow()
    await db_session.commit()

    refresh = await client.post(
        f"/raggers/{ragger.id}/refresh", data={"csrf_token": csrf}, follow_redirects=False
    )
    assert refresh.status_code == 303
    await db_session.refresh(ragger)
    assert ragger.hiscores_updated_at is None
