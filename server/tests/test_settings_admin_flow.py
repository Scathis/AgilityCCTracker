import re

from helpers import _headers

from app.api.v1.status import DD_BREAK_REGEX, DD_CALL_REGEX
from app.models.user import UserRole
from app.web.security import read_flash


async def test_settings_update_persists(client, make_user, login_as, default_group, db_session):
    admin = await make_user(UserRole.ADMIN)
    csrf = login_as(admin)
    original_tiles = default_group.dd_tiles

    response = await client.post(
        "/settings",
        data={
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


async def test_flag_rank_saves_without_any_dd_patterns(
    client, make_user, login_as, default_group, db_session
):
    # The DD call/break patterns moved into code; the form no longer has
    # (or needs) them, so e.g. the gear-flag rank can be saved on its own.
    csrf = login_as(await make_user(UserRole.ADMIN))
    page = await client.get("/settings")
    assert "call_regex" not in page.text
    assert "break_regex" not in page.text

    response = await client.post(
        "/settings",
        data={
            "min_rank": "3",
            "arrive_grace_s": "15",
            "leave_grace_s": "5",
            "max_minutes": "10",
            "flag_viewer_min_rank": "-1",
            "csrf_token": csrf,
        },
        follow_redirects=False,
    )
    assert response.status_code == 303
    await db_session.refresh(default_group)
    assert default_group.flag_viewer_min_rank == -1


async def test_status_sends_the_dd_patterns_from_code(client, fc_group):
    _, token = fc_group
    dd = (await client.get("/api/v1/status", headers=_headers(token))).json()["dd"]
    assert dd["call_regex"] == DD_CALL_REGEX
    assert dd["break_regex"] == DD_BREAK_REGEX

    # Same matching as the plugin: whole message, case-insensitive.
    def call(m):
        return re.fullmatch(DD_CALL_REGEX, m, re.IGNORECASE) is not None

    def brk(m):
        return re.fullmatch(DD_BREAK_REGEX, m, re.IGNORECASE) is not None

    assert call("dd") and call("DD!!") and call(" dd ")
    assert not call("dd at 5") and not call("add")
    assert brk("break dd") and brk("DD brk") and brk("brk dd now")
    assert not brk("break") and not brk("dd")


async def test_settings_page_holds_the_runner_gear_rules(
    client, make_user, login_as, default_group, db_session
):
    admin = await make_user(UserRole.ADMIN)
    csrf = login_as(admin)
    page = await client.get("/settings")
    assert "Runner gear rules" in page.text
    assert "Flag runners with these slots empty" in page.text

    base = {
        "min_rank": "3",
        "arrive_grace_s": "15",
        "leave_grace_s": "5",
        "max_minutes": "10",
        "csrf_token": csrf,
    }
    await client.post("/settings", data={**base, "required": ["BOOTS", "HEAD", "WEAPON"]})
    await db_session.refresh(default_group)
    # WEAPON is handled by the ranged-weapon rule, never as a plain "not empty" slot.
    assert default_group.required_slots == ["HEAD", "BOOTS"]
    assert default_group.require_ranged_weapon is False

    await client.post(
        "/settings",
        data={
            **base,
            "required": ["HEAD", "AMULET", "TORSO", "LEGS", "BOOTS"],
            "ranged_weapon": "on",
        },
    )
    await db_session.refresh(default_group)
    assert default_group.required_slots == ["HEAD", "AMULET", "TORSO", "LEGS", "BOOTS"]
    assert default_group.require_ranged_weapon is True


async def test_staff_cannot_change_gear_rules(client, make_user, login_as):
    csrf = login_as(await make_user(UserRole.STAFF))
    response = await client.post("/settings", data={"required": "HEAD", "csrf_token": csrf})
    assert response.status_code == 403


async def test_settings_checkbox_unchecked_persists_false(
    client, make_user, login_as, default_group, db_session
):
    admin = await make_user(UserRole.ADMIN)
    csrf = login_as(admin)

    # Unchecked checkboxes simply aren't sent in the form body at all.
    await client.post(
        "/settings",
        data={
            "min_rank": "3",
            "arrive_grace_s": "15",
            "leave_grace_s": "5",
            "max_minutes": "10",
            "csrf_token": csrf,
        },
    )
    await db_session.refresh(default_group)
    assert default_group.observer_enabled is False


async def test_admin_reset_password_sets_a_flash_cookie_with_the_password(
    client, make_user, login_as
):
    # Verifies the server-side one-time-reveal mechanism directly (sign ->
    # Set-Cookie -> correct content when decoded) - see docs/BUILD_LOG.md for
    # why this doesn't follow the redirect in this harness.
    admin = await make_user(UserRole.ADMIN)
    target = await make_user(UserRole.VIEWER)
    csrf = login_as(admin)

    response = await client.post(
        f"/admin/users/{target.id}/reset-password",
        data={"csrf_token": csrf},
        follow_redirects=False,
    )
    assert response.status_code == 303
    message = read_flash(response.cookies["flash"])
    assert message is not None
    assert f"Temporary password for '{target.username}'" in message


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


async def test_rank_settings_are_icon_pickers(
    client, make_user, login_as, default_group, db_session
):
    default_group.flag_viewer_min_rank = 2
    default_group.dd_min_rank = 5
    await db_session.commit()
    admin = await make_user(UserRole.ADMIN)
    login_as(admin)

    text = (await client.get("/settings")).text
    assert 'type="number" name="min_rank"' not in text
    assert 'type="number" name="flag_viewer_min_rank"' not in text
    assert re.search(r'name="flag_viewer_min_rank" value="2"\s+checked', text)
    assert re.search(r'name="min_rank" value="5"\s+checked', text)
    assert "ranks/corporal.png" in text and "Anyone in the chat" in text
