"""CLI entry point: `python -m app.cli <command> ...`, per BUILD_PLAN.md §6.3."""

import argparse
import asyncio
import datetime as dt
import secrets
import sys

from sqlalchemy import select

from app.db import async_session_factory
from app.models.fc_group import FcGroup
from app.models.user import User, UserRole
from app.security import generate_token, hash_token
from app.seed import seed_default_loadouts
from app.web.security import hash_password


async def create_admin(username: str) -> None:
    password = secrets.token_urlsafe(12)
    async with async_session_factory() as session:
        existing = await session.execute(select(User).where(User.username == username))
        if existing.scalar_one_or_none() is not None:
            print(f"User '{username}' already exists.", file=sys.stderr)
            sys.exit(1)
        user = User(
            username=username,
            password_hash=hash_password(password),
            role=UserRole.ADMIN,
            created_at=dt.datetime.utcnow(),
        )
        session.add(user)
        await session.commit()
    print(f"Created admin '{username}'. Password (shown once): {password}")


async def create_group(name: str, owner_rsn: str) -> None:
    token = generate_token()
    async with async_session_factory() as session:
        group = FcGroup(
            name=name,
            owner_rsn=owner_rsn,
            token_hash=hash_token(token),
            created_at=dt.datetime.utcnow(),
            observer_enabled=True,
        )
        session.add(group)
        await session.commit()
        await session.refresh(group)
        await seed_default_loadouts(session, group.id)
        await session.commit()
        group_id = group.id
    print(f"Created FC group '{name}' (id={group_id}). Token (shown once): {token}")


async def rotate_token(group_id: int) -> None:
    token = generate_token()
    async with async_session_factory() as session:
        result = await session.execute(select(FcGroup).where(FcGroup.id == group_id))
        group = result.scalar_one_or_none()
        if group is None:
            print(f"No FC group with id {group_id}.", file=sys.stderr)
            sys.exit(1)
        group.token_hash = hash_token(token)
        await session.commit()
    print(f"Rotated token for group {group_id}. New token (shown once): {token}")


async def seed_loadouts_cmd(group_id: int) -> None:
    async with async_session_factory() as session:
        result = await session.execute(select(FcGroup).where(FcGroup.id == group_id))
        if result.scalar_one_or_none() is None:
            print(f"No FC group with id {group_id}.", file=sys.stderr)
            sys.exit(1)
        await seed_default_loadouts(session, group_id)
        await session.commit()
    print(f"Seeded default Running/DD loadouts for group {group_id}.")


def main() -> None:
    parser = argparse.ArgumentParser(prog="python -m app.cli")
    sub = parser.add_subparsers(dest="command", required=True)

    p_admin = sub.add_parser("create-admin")
    p_admin.add_argument("username")

    p_group = sub.add_parser("create-group")
    p_group.add_argument("name")
    p_group.add_argument("owner_rsn")

    p_rotate = sub.add_parser("rotate-token")
    p_rotate.add_argument("group_id", type=int)

    p_seed = sub.add_parser("seed-loadouts")
    p_seed.add_argument("group_id", type=int)

    args = parser.parse_args()

    if args.command == "create-admin":
        asyncio.run(create_admin(args.username))
    elif args.command == "create-group":
        asyncio.run(create_group(args.name, args.owner_rsn))
    elif args.command == "rotate-token":
        asyncio.run(rotate_token(args.group_id))
    elif args.command == "seed-loadouts":
        asyncio.run(seed_loadouts_cmd(args.group_id))


if __name__ == "__main__":
    main()
