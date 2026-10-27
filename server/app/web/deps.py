"""Dashboard auth/RBAC/CSRF dependencies. Separate from app/deps.py, which is
plugin-token auth for the /api/v1/* routes."""

import hmac

from fastapi import Depends, HTTPException, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.fc_group import FcGroup
from app.models.user import User, UserRole, UserStatus
from app.web.security import SESSION_COOKIE_NAME, read_session

ROLE_RANK = {UserRole.VIEWER: 0, UserRole.STAFF: 1, UserRole.ADMIN: 2}


class RequiresLogin(Exception):
    """Raised by require_user when there's no valid session; main.py's
    exception handler turns this into a redirect to /login."""


class RequiresPasswordChange(Exception):
    """Raised by require_user for an account still on an admin-issued
    temporary password; main.py redirects it to /account."""


# The only pages an account on a temporary password can reach.
PASSWORD_CHANGE_PATHS = {"/account", "/account/password", "/logout"}


def _session_payload(request: Request) -> dict | None:
    cookie_value = request.cookies.get(SESSION_COOKIE_NAME)
    if not cookie_value:
        return None
    return read_session(cookie_value)


async def get_current_user(
    request: Request,
    session: AsyncSession = Depends(get_session),
) -> User | None:
    payload = _session_payload(request)
    if payload is None:
        return None
    result = await session.execute(select(User).where(User.id == payload["user_id"]))
    user = result.scalar_one_or_none()
    # A removed or not-yet-approved account has no access, even with a
    # session cookie from before.
    if user is None or user.status != UserStatus.ACTIVE:
        return None
    return user


async def require_user(request: Request, user: User | None = Depends(get_current_user)) -> User:
    if user is None:
        raise RequiresLogin()
    if user.must_change_password and request.url.path not in PASSWORD_CHANGE_PATHS:
        raise RequiresPasswordChange()
    return user


def require_role(min_role: UserRole):
    async def dependency(user: User = Depends(require_user)) -> User:
        if ROLE_RANK[user.role] < ROLE_RANK[min_role]:
            raise HTTPException(status_code=403, detail="Forbidden")
        return user

    return dependency


def get_csrf_token(request: Request) -> str | None:
    payload = _session_payload(request)
    return payload.get("csrf") if payload else None


def template_context(request: Request, user: User | None, **extra) -> dict:
    """Every template needs `request` (Starlette's Jinja2Templates requirement),
    `user` (for the nav bar) and `csrf_token` (for the nav's own logout form,
    present on every authenticated page, not just ones with a "real" form)."""
    return {"request": request, "user": user, "csrf_token": get_csrf_token(request), **extra}


async def get_default_fc_group(session: AsyncSession = Depends(get_session)) -> FcGroup:
    """This dashboard operates on a single FC group per deployment - no
    group-switcher exists in the spec, so every page just uses whichever
    group was created first via `python -m app.cli create-group`."""
    result = await session.execute(select(FcGroup).order_by(FcGroup.id).limit(1))
    group = result.scalar_one_or_none()
    if group is None:
        raise HTTPException(
            status_code=500,
            detail="No FC group configured yet - run: python -m app.cli create-group",
        )
    return group


async def verify_csrf(request: Request, user: User = Depends(require_user)) -> User:
    """Depend on this (instead of/alongside require_role) on every POST route.
    The CSRF token is embedded in the signed session cookie itself (never
    readable by JS - HttpOnly) and must be echoed back as a hidden form
    field, per BUILD_PLAN.md §6.3."""
    payload = _session_payload(request)
    form = await request.form()
    submitted = form.get("csrf_token")
    expected = payload.get("csrf") if payload else None
    if not submitted or not expected or not hmac.compare_digest(str(submitted), expected):
        raise HTTPException(status_code=403, detail="CSRF validation failed")
    return user
