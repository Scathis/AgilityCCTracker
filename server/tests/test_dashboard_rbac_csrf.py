from sqlalchemy import select

from app.models.loadout import Loadout, LoadoutContext
from app.models.user import UserRole


async def _get_running_loadout(db_session, group):
    result = await db_session.execute(
        select(Loadout).where(
            Loadout.fc_group_id == group.id, Loadout.context == LoadoutContext.RUNNING
        )
    )
    return result.scalars().one()


async def test_viewer_can_read_pages(client, make_user, login_as, default_group):
    user = await make_user(UserRole.VIEWER)
    login_as(user)
    for path in ("/events", "/dd/leaderboard", "/loadouts", "/players", "/raggers"):
        response = await client.get(path)
        assert response.status_code == 200, path


async def test_viewer_cannot_start_an_event(client, make_user, login_as, default_group):
    user = await make_user(UserRole.VIEWER)
    csrf = login_as(user)
    response = await client.post("/events", data={"name": "Should fail", "csrf_token": csrf})
    assert response.status_code == 403


async def test_staff_can_start_an_event(
    client, make_user, login_as, default_group, no_active_event
):
    user = await make_user(UserRole.STAFF)
    csrf = login_as(user)
    response = await client.post(
        "/events", data={"name": "Staff event", "csrf_token": csrf}, follow_redirects=False
    )
    assert response.status_code == 303


async def test_staff_cannot_reach_settings(client, make_user, login_as, default_group):
    user = await make_user(UserRole.STAFF)
    login_as(user)
    response = await client.get("/settings")
    assert response.status_code == 403


async def test_viewer_cannot_reach_admin_pages(client, make_user, login_as):
    user = await make_user(UserRole.VIEWER)
    login_as(user)
    response = await client.get("/admin/users")
    assert response.status_code == 403


async def test_admin_can_reach_settings_and_admin_pages(client, make_user, login_as, default_group):
    user = await make_user(UserRole.ADMIN)
    login_as(user)
    for path in ("/settings", "/admin/users", "/admin/groups"):
        response = await client.get(path)
        assert response.status_code == 200, path


async def test_post_without_csrf_token_is_rejected(client, make_user, login_as, default_group):
    user = await make_user(UserRole.STAFF)
    login_as(user)
    response = await client.post("/events", data={"name": "No csrf"})
    assert response.status_code == 403


async def test_post_with_wrong_csrf_token_is_rejected(client, make_user, login_as, default_group):
    user = await make_user(UserRole.STAFF)
    login_as(user)
    response = await client.post("/events", data={"name": "Wrong csrf", "csrf_token": "nope"})
    assert response.status_code == 403


async def test_viewer_cannot_edit_loadout_slots(
    client, make_user, login_as, default_group, db_session
):
    """Covers the loadout slot-editing routes, which only require_role(STAFF)
    at the route level - never previously exercised by a role-check test, as
    opposed to just being read from the source."""
    user = await make_user(UserRole.VIEWER)
    csrf = login_as(user)
    loadout = await _get_running_loadout(db_session, default_group)

    required = await client.post(
        f"/loadouts/{loadout.id}/slots/HEAD/required",
        data={"required": "true", "csrf_token": csrf},
    )
    assert required.status_code == 403

    add_item = await client.post(
        f"/loadouts/{loadout.id}/slots/HEAD/items",
        data={"item_id": "1", "csrf_token": csrf},
    )
    assert add_item.status_code == 403

    remove_item = await client.post(
        f"/loadouts/{loadout.id}/slots/HEAD/items/1/delete",
        data={"csrf_token": csrf},
    )
    assert remove_item.status_code == 403


async def test_viewer_cannot_manage_raggers(client, make_user, login_as, default_group):
    """Covers the raggers routes, which only require_role(STAFF) at the route
    level - never previously exercised by a role-check test."""
    user = await make_user(UserRole.VIEWER)
    csrf = login_as(user)

    add = await client.post(
        "/raggers",
        data={"rsn": "ShouldFail", "threat_notes": "", "csrf_token": csrf},
    )
    assert add.status_code == 403

    status = await client.post(
        "/raggers/1/status",
        data={"status": "confirmed", "csrf_token": csrf},
    )
    assert status.status_code == 403

    sighting = await client.post(
        "/raggers/1/sightings",
        data={"event_id": "", "world": "", "gear_notes": "", "csrf_token": csrf},
    )
    assert sighting.status_code == 403

    refresh = await client.post("/raggers/1/refresh", data={"csrf_token": csrf})
    assert refresh.status_code == 403


async def test_staff_cannot_manage_users_or_groups(
    client, make_user, login_as, default_group
):
    """Covers the admin user/group-management routes, which require_role
    (ADMIN) specifically - staff (one rank below) must be rejected, not just
    an unauthenticated/viewer request."""
    user = await make_user(UserRole.STAFF)
    csrf = login_as(user)

    create = await client.post(
        "/admin/users",
        data={"username": "shouldfail", "role": "viewer", "csrf_token": csrf},
    )
    assert create.status_code == 403

    reset = await client.post("/admin/users/1/reset-password", data={"csrf_token": csrf})
    assert reset.status_code == 403

    rotate = await client.post(
        f"/admin/groups/{default_group.id}/rotate-token", data={"csrf_token": csrf}
    )
    assert rotate.status_code == 403


async def test_viewer_cannot_change_settings(client, make_user, login_as, default_group):
    """update_settings requires ADMIN - viewer (two ranks below) must be
    rejected before any form validation runs."""
    user = await make_user(UserRole.VIEWER)
    csrf = login_as(user)
    response = await client.post(
        "/settings",
        data={
            "call_regex": "x",
            "break_regex": "x",
            "min_rank": "0",
            "arrive_grace_s": "0",
            "leave_grace_s": "0",
            "max_minutes": "60",
            "csrf_token": csrf,
        },
    )
    assert response.status_code == 403
