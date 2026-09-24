from app.models.user import UserRole


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
