
from app.models.kit import Kit
from app.models.user import UserRole


async def _make_kit(db_session, group) -> Kit:
    kit = Kit(fc_group_id=group.id, name="RBAC kit", is_default=False)
    db_session.add(kit)
    await db_session.commit()
    await db_session.refresh(kit)
    return kit


async def test_viewer_can_read_pages(client, make_user, login_as, default_group):
    user = await make_user(UserRole.VIEWER)
    login_as(user)
    for path in ("/events", "/dd/leaderboard"):
        response = await client.get(path)
        assert response.status_code == 200, path


async def test_viewer_cannot_read_kits(
    client, make_user, login_as, default_group, db_session
):
    """Viewer's read access is deliberately scoped to Events + DD Leaderboard
    only - Kits (then Loadouts) and Players were viewer-readable until the owner
    asked for them to be removed from that list."""
    user = await make_user(UserRole.VIEWER)
    login_as(user)
    kit = await _make_kit(db_session, default_group)
    for path in ("/kits", "/kits/new", f"/kits/{kit.id}/edit"):
        response = await client.get(path)
        assert response.status_code == 403, path


async def test_staff_can_still_read_kits(
    client, make_user, login_as, default_group, db_session
):
    user = await make_user(UserRole.STAFF)
    login_as(user)
    kit = await _make_kit(db_session, default_group)
    for path in ("/kits", "/kits/new", f"/kits/{kit.id}/edit"):
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


async def test_viewer_cannot_edit_kits(
    client, make_user, login_as, default_group, db_session
):
    user = await make_user(UserRole.VIEWER)
    csrf = login_as(user)
    kit = await _make_kit(db_session, default_group)
    for path, data in (
        ("/kits", {"name": "x"}),
        (f"/kits/{kit.id}", {"name": "x"}),
        (f"/kits/{kit.id}/default", {}),
        (f"/kits/{kit.id}/delete", {}),
    ):
        response = await client.post(path, data={**data, "csrf_token": csrf})
        assert response.status_code == 403, path


async def test_raggers_and_players_pages_are_gone(client, make_user, login_as, default_group):
    """Ragger tracking and the Players pages (with the hiscores stats
    lookups behind them) were removed 2026-10-27 - not something the FC
    tracks; they must not come back by accident."""
    user = await make_user(UserRole.ADMIN)
    login_as(user)
    for path in ("/raggers", "/players", "/players/1"):
        assert (await client.get(path)).status_code == 404, path
    events_page = (await client.get("/events")).text
    assert "Raggers" not in events_page
    assert 'href="/players"' not in events_page


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
