import datetime as dt
import secrets

import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import engine
from app.main import app
from app.models.fc_group import FcGroup
from app.models.loadout import Loadout
from app.models.user import User, UserRole
from app.security import auth_rate_limiter, hash_token
from app.seed import seed_default_loadouts
from app.web.security import SESSION_COOKIE_NAME, hash_password, new_session_payload, sign_session


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


@pytest_asyncio.fixture
async def default_group(db_session) -> FcGroup:
    """The dashboard has no group-switcher - every route just uses whichever
    FcGroup row is first by id (app.web.deps.get_default_fc_group). Since the
    test DB isn't wiped between tests/files, a test creating its *own* fresh
    FcGroup can't assume the dashboard will actually use it - only the
    globally-first one wins. This fixture returns THAT row (creating one only
    if none exists yet), so dashboard tests build data against the group the
    routes will actually resolve."""
    result = await db_session.execute(select(FcGroup).order_by(FcGroup.id).limit(1))
    group = result.scalar_one_or_none()
    if group is None:
        group = FcGroup(
            name="Default Test FC",
            owner_rsn="DefaultOwner",
            token_hash=hash_token(secrets.token_urlsafe(16)),
            created_at=dt.datetime.utcnow(),
            observer_enabled=True,
        )
        db_session.add(group)
        await db_session.commit()
        await db_session.refresh(group)

    loadout_result = await db_session.execute(
        select(Loadout).where(Loadout.fc_group_id == group.id)
    )
    if loadout_result.scalars().first() is None:
        await seed_default_loadouts(db_session, group.id)
        await db_session.commit()

    return group


@pytest_asyncio.fixture
async def no_active_event(db_session, default_group):
    """Closes out any event left active on default_group by an earlier test.
    Depend on this (alongside default_group) in any test that starts a new
    event via POST /events, since the shared default_group's "only one active
    event" rule would otherwise make test order matter."""
    from app.models.event import Event

    result = await db_session.execute(
        select(Event).where(Event.fc_group_id == default_group.id, Event.ended_at.is_(None))
    )
    for event in result.scalars().all():
        event.ended_at = dt.datetime.utcnow()
    await db_session.commit()


@pytest_asyncio.fixture
async def make_user(db_session):
    """Creates a dashboard User with a given role - password is always
    "testpass123" (only used by the direct login-form tests; everything else
    logs in via the login_as fixture, which skips the password entirely)."""

    async def _make(role: UserRole, username: str | None = None) -> User:
        username = username or f"user_{secrets.token_hex(4)}"
        user = User(
            username=username,
            password_hash=hash_password("testpass123"),
            role=role,
            created_at=dt.datetime.utcnow(),
        )
        db_session.add(user)
        await db_session.commit()
        await db_session.refresh(user)
        return user

    return _make


@pytest_asyncio.fixture
def login_as(client):
    """Sets a valid signed session cookie for `user` directly on `client`,
    skipping the actual /login form - faster than a real login round-trip for
    tests that only care about what happens *after* auth. Returns the CSRF
    token to include in that session's POST forms."""

    def _login(user: User) -> str:
        payload = new_session_payload(user.id)
        client.cookies.set(SESSION_COOKIE_NAME, sign_session(payload))
        return payload["csrf"]

    return _login
