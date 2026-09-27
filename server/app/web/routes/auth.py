import datetime as dt

from fastapi import APIRouter, Depends, Form, HTTPException, Request
from fastapi import status as http_status
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.user import User
from app.security import dashboard_login_rate_limiter
from app.web.deps import get_current_user, template_context, verify_csrf
from app.web.security import (
    SESSION_COOKIE_NAME,
    SESSION_MAX_AGE_SECONDS,
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
