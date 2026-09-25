from fastapi import Depends, Header, HTTPException, Request
from fastapi import status as http_status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.fc_group import FcGroup
from app.security import auth_rate_limiter, hash_token


def _client_key(request: Request) -> str:
    return request.client.host if request.client else "unknown"


async def get_current_fc_group(
    request: Request,
    authorization: str | None = Header(default=None),
    session: AsyncSession = Depends(get_session),
) -> FcGroup:
    """Plugin token auth (BUILD_PLAN.md §6.3): Bearer token, hashed comparison,
    5-failures-per-5-minutes-per-IP rate limit -> 429."""
    client_key = _client_key(request)
    if auth_rate_limiter.is_limited(client_key):
        raise HTTPException(
            status_code=http_status.HTTP_429_TOO_MANY_REQUESTS, detail="Too many failed attempts"
        )

    if not authorization or not authorization.startswith("Bearer "):
        auth_rate_limiter.record_failure(client_key)
        raise HTTPException(
            status_code=http_status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token"
        )

    token = authorization[len("Bearer ") :]
    token_hash = hash_token(token)
    result = await session.execute(select(FcGroup).where(FcGroup.token_hash == token_hash))
    fc_group = result.scalar_one_or_none()
    if fc_group is None:
        auth_rate_limiter.record_failure(client_key)
        raise HTTPException(status_code=http_status.HTTP_401_UNAUTHORIZED, detail="Invalid token")

    return fc_group
