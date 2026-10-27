"""Self-registration, approval, role changes, removal and the owner guard."""

import secrets

from sqlalchemy import select

from app.models.user import User, UserRole, UserStatus

PASSWORD = "correct horse battery"


def _name() -> str:
    return f"reg_{secrets.token_hex(4)}"


async def _get(db_session, username: str) -> User | None:
    db_session.expire_all()
    result = await db_session.execute(select(User).where(User.username == username))
    return result.scalar_one_or_none()


async def _register(client, username: str, password: str = PASSWORD, confirm: str | None = None):
    return await client.post(
        "/register",
        data={"username": username, "password": password, "confirm": confirm or password},
    )


async def test_register_creates_a_pending_account_that_cannot_log_in(client, db_session):
    username = _name()
    response = await _register(client, username)
    assert response.status_code == 200
    assert "Request sent" in response.text

    user = await _get(db_session, username)
    assert user.status == UserStatus.PENDING
    assert user.role == UserRole.VIEWER

    login = await client.post("/login", data={"username": username, "password": PASSWORD})
    assert login.status_code == 403
    assert "waiting for an admin" in login.text
    assert "session" not in login.cookies


async def test_register_validation(client, db_session):
    assert (await _register(client, "ab")).status_code == 400
    assert (await _register(client, _name(), "short")).status_code == 400
    assert (await _register(client, _name(), PASSWORD, PASSWORD + "x")).status_code == 400
    taken = _name()
    assert (await _register(client, taken)).status_code == 200
    again = await _register(client, taken)
    assert again.status_code == 400
    assert "taken" in again.text


async def test_register_is_rate_limited_per_ip(client):
    for _ in range(3):
        assert "Request sent" in (await _register(client, _name())).text
    blocked = await _register(client, _name())
    assert blocked.status_code == 400
    assert "Too many sign-ups" in blocked.text


async def test_admin_approves_with_a_role_and_the_user_can_log_in(
    client, make_user, login_as, db_session
):
    username = _name()
    await _register(client, username)
    pending = await _get(db_session, username)

    admin = await make_user(UserRole.ADMIN)
    csrf = login_as(admin)
    page = await client.get("/admin/users")
    assert username in page.text
    response = await client.post(
        f"/admin/users/{pending.id}/approve",
        data={"role": "staff", "csrf_token": csrf},
        follow_redirects=False,
    )
    assert response.status_code == 303

    approved = await _get(db_session, username)
    assert approved.status == UserStatus.ACTIVE
    assert approved.role == UserRole.STAFF

    client.cookies.clear()
    login = await client.post(
        "/login", data={"username": username, "password": PASSWORD}, follow_redirects=False
    )
    assert login.status_code == 303


async def test_admin_rejects_a_request(client, make_user, login_as, db_session):
    username = _name()
    await _register(client, username)
    pending = await _get(db_session, username)
    csrf = login_as(await make_user(UserRole.ADMIN))
    await client.post(f"/admin/users/{pending.id}/reject", data={"csrf_token": csrf})
    assert await _get(db_session, username) is None


async def test_admin_changes_role_and_removes_an_account(
    client, make_user, login_as, db_session
):
    target = await make_user(UserRole.VIEWER)
    csrf = login_as(await make_user(UserRole.ADMIN))

    await client.post(f"/admin/users/{target.id}/role", data={"role": "admin", "csrf_token": csrf})
    assert (await _get(db_session, target.username)).role == UserRole.ADMIN

    await client.post(f"/admin/users/{target.id}/delete", data={"csrf_token": csrf})
    assert await _get(db_session, target.username) is None


async def test_removed_account_loses_access_immediately(client, make_user, login_as, db_session):
    target = await make_user(UserRole.STAFF)
    admin = await make_user(UserRole.ADMIN)
    admin_csrf = login_as(admin)
    await client.post(f"/admin/users/{target.id}/delete", data={"csrf_token": admin_csrf})

    login_as(target)  # a session cookie from before the removal
    response = await client.get("/events", follow_redirects=False)
    assert response.status_code in (302, 303, 307)
    assert "/login" in response.headers["location"]


async def test_owner_account_cannot_be_changed_or_removed(client, make_user, login_as, db_session):
    owner = await make_user(UserRole.ADMIN)
    owner.protected = True
    await db_session.commit()
    csrf = login_as(await make_user(UserRole.ADMIN))

    for path, data in (
        (f"/admin/users/{owner.id}/role", {"role": "viewer"}),
        (f"/admin/users/{owner.id}/delete", {}),
        (f"/admin/users/{owner.id}/reset-password", {}),
    ):
        await client.post(path, data={**data, "csrf_token": csrf})

    still = await _get(db_session, owner.username)
    assert still is not None
    assert still.role == UserRole.ADMIN
    assert still.password_hash == owner.password_hash


async def test_admin_cannot_demote_or_remove_themselves(client, make_user, login_as, db_session):
    admin = await make_user(UserRole.ADMIN)
    csrf = login_as(admin)
    await client.post(f"/admin/users/{admin.id}/role", data={"role": "viewer", "csrf_token": csrf})
    await client.post(f"/admin/users/{admin.id}/delete", data={"csrf_token": csrf})
    me = await _get(db_session, admin.username)
    assert me is not None
    assert me.role == UserRole.ADMIN


async def test_change_own_password(client, make_user, login_as, db_session):
    user = await make_user(UserRole.VIEWER)  # password "testpass123"
    csrf = login_as(user)

    wrong = await client.post(
        "/account/password",
        data={"current": "nope", "password": PASSWORD, "confirm": PASSWORD, "csrf_token": csrf},
    )
    assert wrong.status_code == 400

    ok = await client.post(
        "/account/password",
        data={
            "current": "testpass123",
            "password": PASSWORD,
            "confirm": PASSWORD,
            "csrf_token": csrf,
        },
        follow_redirects=False,
    )
    assert ok.status_code == 303

    client.cookies.clear()
    login = await client.post(
        "/login", data={"username": user.username, "password": PASSWORD}, follow_redirects=False
    )
    assert login.status_code == 303
