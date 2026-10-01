import datetime as dt

from sqlalchemy import select

from app.models.item import Item
from app.models.loadout import Loadout, LoadoutContext, LoadoutRequirement
from app.models.player import Player
from app.models.ragger import Ragger
from app.models.user import UserRole


async def _get_running_loadout(db_session, group) -> Loadout:
    result = await db_session.execute(
        select(Loadout).where(
            Loadout.fc_group_id == group.id, Loadout.context == LoadoutContext.RUNNING
        )
    )
    return result.scalars().one()


async def test_loadout_list_and_detail_render(
    client, make_user, login_as, default_group, db_session
):
    user = await make_user(UserRole.VIEWER)
    login_as(user)

    list_response = await client.get("/loadouts")
    assert list_response.status_code == 200
    assert "Running" in list_response.text

    loadout = await _get_running_loadout(db_session, default_group)
    detail_response = await client.get(f"/loadouts/{loadout.id}")
    assert detail_response.status_code == 200
    # The full 11-slot equipment grid always renders, regardless of
    # whether any slot has a configured requirement yet (seeding no
    # longer pre-populates default rules - see docs/BUILD_LOG.md).
    assert "slot-WEAPON" in detail_response.text
    assert "slot-HEAD" in detail_response.text


async def test_search_input_is_wired_with_a_name_attribute(
    client, make_user, login_as, default_group, db_session
):
    """Regression test: the search <input> originally had no name="q", so
    HTMX never actually sent the typed text as a query param - every
    search silently returned the "type to search" empty state regardless
    of input, which is what the owner reported as "search doesn't work" in
    real use. Catches this by asserting the rendered HTML, not just the
    search endpoint's own behavior in isolation (which worked fine on its
    own and didn't catch the wiring bug)."""
    staff = await make_user(UserRole.STAFF)
    login_as(staff)
    loadout = await _get_running_loadout(db_session, default_group)

    response = await client.get(f"/loadouts/{loadout.id}")
    assert 'name="q"' in response.text
    assert 'type="search"' in response.text


async def test_staff_can_require_a_slot_with_no_allow_list(
    client, make_user, login_as, default_group, db_session
):
    staff = await make_user(UserRole.STAFF)
    csrf = login_as(staff)
    loadout = await _get_running_loadout(db_session, default_group)

    response = await client.post(
        f"/loadouts/{loadout.id}/slots/HEAD/required",
        data={"required": "true", "csrf_token": csrf},
        follow_redirects=False,
    )
    assert response.status_code == 303

    result = await db_session.execute(
        select(LoadoutRequirement).where(
            LoadoutRequirement.loadout_id == loadout.id, LoadoutRequirement.slot == "HEAD"
        )
    )
    req = result.scalar_one()
    assert req.required is True
    assert req.allowed_item_ids in (None, [])


async def test_staff_can_add_and_remove_an_allowed_item(
    client, make_user, login_as, default_group, db_session
):
    staff = await make_user(UserRole.STAFF)
    csrf = login_as(staff)
    loadout = await _get_running_loadout(db_session, default_group)

    item = Item(id=841, name="Magic shortbow", updated_at=dt.datetime.utcnow())
    db_session.add(item)
    await db_session.commit()

    add = await client.post(
        f"/loadouts/{loadout.id}/slots/WEAPON/items",
        data={"item_id": "841", "csrf_token": csrf},
        follow_redirects=False,
    )
    assert add.status_code == 303

    result = await db_session.execute(
        select(LoadoutRequirement).where(
            LoadoutRequirement.loadout_id == loadout.id, LoadoutRequirement.slot == "WEAPON"
        )
    )
    req = result.scalar_one()
    assert req.allowed_item_ids == [841]

    detail = await client.get(f"/loadouts/{loadout.id}")
    assert "Magic shortbow" in detail.text

    remove = await client.post(
        f"/loadouts/{loadout.id}/slots/WEAPON/items/841/delete",
        data={"csrf_token": csrf},
        follow_redirects=False,
    )
    assert remove.status_code == 303

    result = await db_session.execute(
        select(LoadoutRequirement).where(
            LoadoutRequirement.loadout_id == loadout.id, LoadoutRequirement.slot == "WEAPON"
        )
    )
    # Not required and now empty - the row is dropped entirely (back to unrestricted).
    assert result.scalar_one_or_none() is None


async def test_adding_an_unknown_item_id_is_rejected(
    client, make_user, login_as, default_group, db_session
):
    staff = await make_user(UserRole.STAFF)
    csrf = login_as(staff)
    loadout = await _get_running_loadout(db_session, default_group)

    response = await client.post(
        f"/loadouts/{loadout.id}/slots/WEAPON/items",
        data={"item_id": "999999999", "csrf_token": csrf},
    )
    assert response.status_code == 404


async def test_item_search_returns_html_fragment(
    client, make_user, login_as, default_group, db_session
):
    user = await make_user(UserRole.VIEWER)
    login_as(user)
    loadout = await _get_running_loadout(db_session, default_group)

    response = await client.get(
        f"/loadouts/{loadout.id}/slots/WEAPON/search", params={"q": "nonexistent item xyz"}
    )
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
