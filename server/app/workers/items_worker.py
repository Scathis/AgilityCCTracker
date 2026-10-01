"""Items background worker. Daily: fetch the OSRS Wiki's item mapping
(id/name only) and upsert into `items`, which the loadout picker's item
search runs against. Deliberately never fetches or stores prices (dropped
by owner request 2026-10-01 - both unused dead weight and a Plugin Hub
approval risk, see docs/BUILD_LOG.md): a gear/risk-value calculator reads
as a tool for identifying high-value PK targets, which this project
specifically does not want to be.
"""

import asyncio
import datetime as dt
import logging

import httpx
from sqlalchemy import select

from app.config import settings
from app.db import async_session_factory
from app.models.item import Item

logger = logging.getLogger(__name__)

MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping"
POLL_INTERVAL_S = 24 * 3600


def parse_item_mapping(mapping: list[dict]) -> list[dict]:
    """Pure - just the id/name pairs, dropping everything else the mapping
    API returns (including the highalch/price-adjacent fields)."""
    result = []
    for entry in mapping:
        item_id = entry.get("id")
        if item_id is None:
            continue
        result.append({"id": item_id, "name": entry.get("name", "")})
    return result


async def fetch_items() -> list[dict]:
    async with httpx.AsyncClient(
        timeout=30.0, headers={"User-Agent": settings.user_agent}
    ) as client:
        response = await client.get(MAPPING_URL)
        response.raise_for_status()
    return parse_item_mapping(response.json())


async def run_once() -> None:
    items = await fetch_items()
    now = dt.datetime.utcnow()
    async with async_session_factory() as session:
        for entry in items:
            result = await session.execute(select(Item).where(Item.id == entry["id"]))
            existing = result.scalar_one_or_none()
            if existing is None:
                session.add(Item(id=entry["id"], name=entry["name"], updated_at=now))
            else:
                existing.name = entry["name"]
                existing.updated_at = now
        await session.commit()


async def items_worker_loop() -> None:
    while True:
        try:
            await run_once()
        except Exception:
            logger.exception("items worker iteration failed")
        await asyncio.sleep(POLL_INTERVAL_S)
