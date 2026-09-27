from app.models.user import UserRole


async def test_login_succeeds_and_sets_session_cookie(client, make_user):
    await make_user(UserRole.VIEWER, username="loginok")
    response = await client.post("/login", data={"username": "loginok", "password": "testpass123"})
    assert response.status_code == 303
    assert "session" in response.cookies


async def test_login_fails_with_wrong_password(client, make_user):
    await make_user(UserRole.VIEWER, username="loginbad")
    response = await client.post("/login", data={"username": "loginbad", "password": "wrong"})
    assert response.status_code == 401


async def test_login_fails_for_unknown_user(client):
    response = await client.post("/login", data={"username": "nosuchuser", "password": "x"})
    assert response.status_code == 401


async def test_unauthenticated_access_redirects_to_login(client):
    response = await client.get("/events", follow_redirects=False)
    assert response.status_code == 303
    assert response.headers["location"] == "/login"


async def test_root_redirects_to_login_when_logged_out(client):
    response = await client.get("/", follow_redirects=False)
    assert response.status_code == 303
    assert response.headers["location"] == "/login"


async def test_root_redirects_to_events_when_logged_in(client, make_user, login_as):
    user = await make_user(UserRole.VIEWER, username="rootredirect")
    login_as(user)
    response = await client.get("/", follow_redirects=False)
    assert response.status_code == 303
    assert response.headers["location"] == "/events"


async def test_logout_requires_csrf_and_clears_session(client, make_user, login_as):
    user = await make_user(UserRole.VIEWER, username="logoutuser")
    csrf = login_as(user)

    # Wrong/missing CSRF is rejected.
    bad = await client.post("/logout", data={"csrf_token": "wrong"})
    assert bad.status_code == 403

    good = await client.post("/logout", data={"csrf_token": csrf}, follow_redirects=False)
    assert good.status_code == 303
    assert good.headers["location"] == "/login"


async def test_login_rate_limited_after_five_failures(client):
    for _ in range(5):
        response = await client.post("/login", data={"username": "nosuchuser", "password": "x"})
        assert response.status_code == 401

    limited = await client.post("/login", data={"username": "nosuchuser", "password": "x"})
    assert limited.status_code == 429
