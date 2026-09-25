import datetime as dt
import secrets

from fastapi import APIRouter, Depends, Form, HTTPException, Request
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.fc_group import FcGroup
from app.models.user import User, UserRole
from app.security import generate_token, hash_token
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
    context = template_context(request, user, users=users, flash=_read_flash(request))
    response = templates.TemplateResponse(request, "admin/users.html", context)
    response.delete_cookie(FLASH_COOKIE_NAME)
    return response


@router.post("/admin/users")
async def create_user(
    username: str = Form(...),
    role: str = Form(...),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.ADMIN)),
    _csrf: User = Depends(verify_csrf),
):
    existing = await session.execute(select(User).where(User.username == username))
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(status_code=400, detail="Username taken")
    password = secrets.token_urlsafe(12)
    session.add(
        User(
            username=username,
            password_hash=hash_password(password),
            role=UserRole(role),
            created_at=dt.datetime.utcnow(),
        )
    )
    await session.commit()
    return _flash_and_redirect(
        "/admin/users", f"Password for '{username}' (shown once): {password}"
    )


@router.post("/admin/users/{user_id}/reset-password")
async def reset_password(
    user_id: int,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.ADMIN)),
    _csrf: User = Depends(verify_csrf),
):
    result = await session.execute(select(User).where(User.id == user_id))
    target = result.scalar_one_or_none()
    if target is None:
        raise HTTPException(status_code=404, detail="User not found")
    password = secrets.token_urlsafe(12)
    target.password_hash = hash_password(password)
    await session.commit()
    return _flash_and_redirect(
        "/admin/users", f"New password for '{target.username}' (shown once): {password}"
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
