from app.models.user import UserRole
from app.web.security import read_flash


async def test_settings_update_persists(client, make_user, login_as, default_group, db_session):
    admin = await make_user(UserRole.ADMIN)
    csrf = login_as(admin)
    original_tiles = default_group.dd_tiles

    response = await client.post(
        "/settings",
        data={
            "call_regex": r"^\s*dd\s*!*$",
            "break_regex": r"^\s*(break|brk)\s*(dd)?\s*!*$",
            "min_rank": "3",
            "arrive_grace_s": "15",
            "leave_grace_s": "5",
            "max_minutes": "10",
            "observer_enabled": "on",
            "csrf_token": csrf,
        },
        follow_redirects=False,
    )
    assert response.status_code == 303

    await db_session.refresh(default_group)
    # DD tiles are deliberately not settable from this form - confirm a
    # settings save leaves them untouched, not just that the (removed)
    # tile fields are absent from the request.
    assert default_group.dd_tiles == original_tiles
    assert default_group.dd_min_rank == 3
    assert default_group.observer_enabled is True


async def test_settings_checkbox_unchecked_persists_false(
    client, make_user, login_as, default_group, db_session
):
    admin = await make_user(UserRole.ADMIN)
    csrf = login_as(admin)

    # Unchecked checkboxes simply aren't sent in the form body at all.
    await client.post(
        "/settings",
        data={
            "call_regex": "dd",
            "break_regex": "brk",
            "min_rank": "3",
            "arrive_grace_s": "15",
            "leave_grace_s": "5",
            "max_minutes": "10",
            "csrf_token": csrf,
        },
    )
    await db_session.refresh(default_group)
    assert default_group.observer_enabled is False


async def test_admin_create_user_sets_a_flash_cookie_with_the_password(client, make_user, login_as):
    # Verifies the server-side one-time-reveal mechanism directly (sign ->
    # Set-Cookie -> correct content when decoded), rather than relying on the
    # test client reusing that Set-Cookie across separate requests - that
    # part proved genuinely inconsistent in this harness (two structurally
    # identical tests, one passed and one didn't, under both automatic
    # follow_redirects and an explicit client.cookies.set() workaround - see
    # docs/BUILD_LOG.md). A real browser follows Set-Cookie-on-redirect
    # correctly per HTTP spec; that part isn't this project's code to prove.
    admin = await make_user(UserRole.ADMIN)
    csrf = login_as(admin)

    response = await client.post(
        "/admin/users",
        data={"username": "newlycreated", "role": "staff", "csrf_token": csrf},
        follow_redirects=False,
    )
    assert response.status_code == 303
    assert "flash" in response.cookies
    message = read_flash(response.cookies["flash"])
    assert message is not None
    assert "Password for 'newlycreated'" in message


async def test_admin_rotate_group_token_sets_a_flash_cookie_with_the_token(
    client, make_user, login_as, default_group
):
    admin = await make_user(UserRole.ADMIN)
    csrf = login_as(admin)

    response = await client.post(
        f"/admin/groups/{default_group.id}/rotate-token",
        data={"csrf_token": csrf},
        follow_redirects=False,
    )
    assert response.status_code == 303
    assert "flash" in response.cookies
    message = read_flash(response.cookies["flash"])
    assert message is not None
    assert "New token for" in message
