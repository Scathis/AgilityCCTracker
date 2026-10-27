import datetime as dt

from fastapi import APIRouter, Depends, Form, HTTPException, Request
from fastapi import status as http_status
from fastapi.responses import RedirectResponse
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.user import User, UserRole, UserStatus
from app.security import dashboard_login_rate_limiter, registration_rate_limiter
from app.web.accounts import MAX_PENDING, password_error, username_error
from app.web.deps import get_current_user, require_user, template_context, verify_csrf
from app.web.security import (
    SESSION_COOKIE_NAME,
    SESSION_MAX_AGE_SECONDS,
    hash_password,
    new_session_payload,
    sign_session,
    verify_password,
)
from app.web.templating import templates

router = APIRouter()


def _client_key(request: Request) -> str:
    return request.client.host if request.client else "unknown"


@router.get("/login")
async def login_form(request: Request, user: User | None = Depends(get_current_user)):
    if user is not None:
        return RedirectResponse(url="/events", status_code=303)
    return templates.TemplateResponse(
        request, "login.html", template_context(request, None, error=None)
    )


@router.post("/login")
async def login_submit(
    request: Request,
    username: str = Form(...),
    password: str = Form(...),
    session: AsyncSession = Depends(get_session),
):
    client_key = _client_key(request)
    if dashboard_login_rate_limiter.is_limited(client_key):
        raise HTTPException(
            status_code=http_status.HTTP_429_TOO_MANY_REQUESTS, detail="Too many failed attempts"
        )

    result = await session.execute(select(User).where(User.username == username))
    user = result.scalar_one_or_none()
    if user is None or not verify_password(password, user.password_hash):
        dashboard_login_rate_limiter.record_failure(client_key)
        context = template_context(request, None, error="Invalid username or password")
        return templates.TemplateResponse(request, "login.html", context, status_code=401)
    if user.status != UserStatus.ACTIVE:
        # Only said after a correct password, so it doesn't reveal which
        # usernames are waiting.
        context = template_context(
            request, None, error="Your account is waiting for an admin to approve it."
        )
        return templates.TemplateResponse(request, "login.html", context, status_code=403)

    user.last_login_at = dt.datetime.utcnow()
    await session.commit()

    payload = new_session_payload(user.id)
    response = RedirectResponse(url="/events", status_code=303)
    response.set_cookie(
        SESSION_COOKIE_NAME,
        sign_session(payload),
        httponly=True,
        samesite="lax",
        secure=request.url.scheme == "https",
        max_age=SESSION_MAX_AGE_SECONDS,
    )
    return response


@router.post("/logout")
async def logout(user: User = Depends(verify_csrf)):
    response = RedirectResponse(url="/login", status_code=303)
    response.delete_cookie(SESSION_COOKIE_NAME)
    return response


def _register_page(request: Request, error: str | None = None, done: bool = False, **form):
    context = template_context(request, None, error=error, done=done, form=form)
    return templates.TemplateResponse(
        request, "register.html", context, status_code=400 if error else 200
    )


@router.get("/register")
async def register_form(request: Request, user: User | None = Depends(get_current_user)):
    if user is not None:
        return RedirectResponse(url="/events", status_code=303)
    return _register_page(request)


@router.post("/register")
async def register_submit(
    request: Request,
    username: str = Form(...),
    password: str = Form(...),
    confirm: str = Form(...),
    session: AsyncSession = Depends(get_session),
):
    """Creates a PENDING account: no access until an admin approves it and
    picks its role on the Admin page. Limited per IP (in memory) and by the
    size of the approval queue."""
    client_key = _client_key(request)
    if registration_rate_limiter.is_limited(client_key):
        return _register_page(
            request, "Too many sign-ups from here - try again later.", username=username
        )
    username = username.strip()
    error = username_error(username) or password_error(password, confirm, username)
    if error:
        return _register_page(request, error, username=username)

    existing = await session.execute(select(User).where(User.username == username))
    if existing.scalar_one_or_none() is not None:
        return _register_page(request, "That username is taken.", username=username)
    pending = await session.execute(
        select(func.count()).select_from(User).where(User.status == UserStatus.PENDING)
    )
    if pending.scalar_one() >= MAX_PENDING:
        return _register_page(
            request, "Too many accounts are waiting for approval - try again later."
        )

    session.add(
        User(
            username=username,
            password_hash=hash_password(password),
            role=UserRole.VIEWER,
            status=UserStatus.PENDING,
            created_at=dt.datetime.utcnow(),
        )
    )
    await session.commit()
    registration_rate_limiter.record_failure(client_key)
    return _register_page(request, done=True)


def _account_page(request: Request, user: User, error: str | None = None, saved: bool = False):
    context = template_context(request, user, error=error, saved=saved)
    return templates.TemplateResponse(
        request, "account.html", context, status_code=400 if error else 200
    )


@router.get("/account")
async def account_page(request: Request, user: User = Depends(require_user)):
    return _account_page(request, user, saved=request.query_params.get("saved") is not None)


@router.post("/account/password")
async def change_password(
    request: Request,
    current: str = Form(...),
    password: str = Form(...),
    confirm: str = Form(...),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(verify_csrf),
):
    client_key = _client_key(request)
    if dashboard_login_rate_limiter.is_limited(client_key):
        raise HTTPException(
            status_code=http_status.HTTP_429_TOO_MANY_REQUESTS, detail="Too many failed attempts"
        )
    if not verify_password(current, user.password_hash):
        dashboard_login_rate_limiter.record_failure(client_key)
        return _account_page(request, user, "Your current password is wrong.")
    error = password_error(password, confirm, user.username)
    if error:
        return _account_page(request, user, error)
    db_user = await session.get(User, user.id)
    db_user.password_hash = hash_password(password)
    await session.commit()
    return RedirectResponse(url="/account?saved=1", status_code=303)
