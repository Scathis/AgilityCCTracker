import datetime as dt
import secrets

import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import engine
from app.main import app
from app.models.fc_group import FcGroup
from app.security import auth_rate_limiter, hash_token


@pytest_asyncio.fixture(autouse=True)
async def reset_rate_limiter():
    auth_rate_limiter.clear_all()
    yield
    auth_rate_limiter.clear_all()


@pytest_asyncio.fixture
async def db_session():
    """MySQL defaults to REPEATABLE READ, which would let this long-lived test
    session's first query fix a snapshot that never sees rows committed by
    the app's own separate per-request sessions afterwards (each POST to the
    test client runs its own request-scoped session/transaction). READ
    COMMITTED makes every new statement here see the latest committed state,
    matching what a human manually re-querying the database would expect."""
    async with engine.connect() as conn:
        conn = await conn.execution_options(isolation_level="READ COMMITTED")
        async with AsyncSession(bind=conn, expire_on_commit=False) as session:
            yield session


@pytest_asyncio.fixture
async def fc_group(db_session):
    """A fresh FcGroup + its plaintext token per test - unique names/tokens so
    tests never collide even though the test DB isn't wiped between tests."""
    token = secrets.token_urlsafe(16)
    group = FcGroup(
        name=f"Test FC {secrets.token_hex(4)}",
        owner_rsn="Owner Rsn",
        token_hash=hash_token(token),
        created_at=dt.datetime.utcnow(),
        observer_enabled=True,
    )
    db_session.add(group)
    await db_session.commit()
    await db_session.refresh(group)
    return group, token


@pytest_asyncio.fixture
async def client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac
