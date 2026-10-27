import secrets

from fastapi import APIRouter, Depends, Form, HTTPException, Request
from fastapi.responses import RedirectResponse
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.dd import DdCall
from app.models.event import Event
from app.models.fc_group import FcGroup
from app.models.loadout import LoadoutCheck
from app.models.user import User, UserRole, UserStatus
from app.security import generate_token, hash_token
from app.web.accounts import admin_change_error, parse_role
from app.web.deps import require_role, template_context, verify_csrf
from app.web.security import FLASH_COOKIE_NAME, hash_password, read_flash, sign_flash
from app.web.templating import templates

router = APIRouter()


def _flash_and_redirect(url: str, message: str) -> RedirectResponse:
    response = RedirectResponse(url=url, status_code=303)
    response.set_cookie(FLASH_COOKIE_NAME, sign_flash(message), httponly=True, max_age=60)
    return response


def _read_flash(request: Request) -> str | None:
    cookie_value = request.cookies.get(FLASH_COOKIE_NAME)
    return read_flash(cookie_value) if cookie_value else None


@router.get("/admin/users")
async def list_users(
    request: Request,
    user: User = Depends(require_role(UserRole.ADMIN)),
    session: AsyncSession = Depends(get_session),
):
    result = await session.execute(select(User).order_by(User.username))
    users = result.scalars().all()
    pending = sorted(
        (u for u in users if u.status == UserStatus.PENDING), key=lambda u: u.created_at
    )
    active = [u for u in users if u.status == UserStatus.ACTIVE]
    context = template_context(
        request,
        user,
        pending=pending,
        active=active,
        roles=list(UserRole),
        flash=_read_flash(request),
    )
    response = templates.TemplateResponse(request, "admin/users.html", context)
    response.delete_cookie(FLASH_COOKIE_NAME)
    return response


async def _target(session: AsyncSession, user_id: int) -> User:
    target = await session.get(User, user_id)
    if target is None:
        raise HTTPException(status_code=404, detail="User not found")
    return target


@router.post("/admin/users/{user_id}/approve")
async def approve_user(
    user_id: int,
    role: str = Form(...),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.ADMIN)),
    _csrf: User = Depends(verify_csrf),
):
    target = await _target(session, user_id)
    new_role = parse_role(role)
    if target.status != UserStatus.PENDING or new_role is None:
        return _flash_and_redirect("/admin/users", "That request can't be approved.")
    target.status = UserStatus.ACTIVE
    target.role = new_role
    await session.commit()
    return _flash_and_redirect(
        "/admin/users", f"Approved '{target.username}' as {new_role.value}."
    )


@router.post("/admin/users/{user_id}/reject")
async def reject_user(
    user_id: int,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.ADMIN)),
    _csrf: User = Depends(verify_csrf),
):
    target = await _target(session, user_id)
    if target.status != UserStatus.PENDING:
        return _flash_and_redirect("/admin/users", "Only pending requests can be rejected.")
    await session.delete(target)
    await session.commit()
    return _flash_and_redirect("/admin/users", f"Rejected '{target.username}'.")


@router.post("/admin/users/{user_id}/role")
async def change_role(
    user_id: int,
    role: str = Form(...),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.ADMIN)),
    _csrf: User = Depends(verify_csrf),
):
    target = await _target(session, user_id)
    new_role = parse_role(role)
    error = admin_change_error(user, target) or (None if new_role else "Unknown role.")
    if error:
        return _flash_and_redirect("/admin/users", error)
    target.role = new_role
    await session.commit()
    return _flash_and_redirect(
        "/admin/users", f"'{target.username}' is now {new_role.value}."
    )


@router.post("/admin/users/{user_id}/delete")
async def delete_user(
    user_id: int,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.ADMIN)),
    _csrf: User = Depends(verify_csrf),
):
    target = await _target(session, user_id)
    error = admin_change_error(user, target)
    if error:
        return _flash_and_redirect("/admin/users", error)
    # Keep the records they touched (events started, DD edits, manual gear
    # notes) - just forget who did it.
    await session.execute(
        update(Event).where(Event.started_by_user_id == target.id).values(started_by_user_id=None)
    )
    await session.execute(
        update(DdCall).where(DdCall.edited_by_user_id == target.id).values(edited_by_user_id=None)
    )
    await session.execute(
        update(LoadoutCheck)
        .where(LoadoutCheck.reported_by_user_id == target.id)
        .values(reported_by_user_id=None)
    )
    await session.delete(target)
    await session.commit()
    return _flash_and_redirect("/admin/users", f"Removed '{target.username}'.")


@router.post("/admin/users/{user_id}/reset-password")
async def reset_password(
    user_id: int,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.ADMIN)),
    _csrf: User = Depends(verify_csrf),
):
    """For someone who's forgotten theirs: a one-time password to pass on,
    which they then change on their Account page."""
    target = await _target(session, user_id)
    error = admin_change_error(user, target)
    if error:
        return _flash_and_redirect("/admin/users", error)
    password = secrets.token_urlsafe(12)
    target.password_hash = hash_password(password)
    await session.commit()
    return _flash_and_redirect(
        "/admin/users",
        f"Temporary password for '{target.username}' (shown once): {password} - "
        "ask them to change it on their Account page.",
    )


@router.get("/admin/groups")
async def list_groups(
    request: Request,
    user: User = Depends(require_role(UserRole.ADMIN)),
    session: AsyncSession = Depends(get_session),
):
    result = await session.execute(select(FcGroup).order_by(FcGroup.id))
    groups = result.scalars().all()
    context = template_context(request, user, groups=groups, flash=_read_flash(request))
    response = templates.TemplateResponse(request, "admin/groups.html", context)
    response.delete_cookie(FLASH_COOKIE_NAME)
    return response


@router.post("/admin/groups/{group_id}/rotate-token")
async def rotate_group_token(
    group_id: int,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.ADMIN)),
    _csrf: User = Depends(verify_csrf),
):
    result = await session.execute(select(FcGroup).where(FcGroup.id == group_id))
    group = result.scalar_one_or_none()
    if group is None:
        raise HTTPException(status_code=404, detail="Group not found")
    token = generate_token()
    group.token_hash = hash_token(token)
    await session.commit()
    return _flash_and_redirect(
        "/admin/groups", f"New token for '{group.name}' (shown once): {token}"
    )
