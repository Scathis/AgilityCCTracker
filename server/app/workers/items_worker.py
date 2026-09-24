"""Items background worker - BUILD_PLAN.md §6.6. Daily: fetch the OSRS Wiki
Real-time Prices API's mapping + latest prices, upsert into `items`.
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
LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest"
POLL_INTERVAL_S = 24 * 3600


def merge_item_data(mapping: list[dict], latest: dict) -> list[dict]:
    """Pure - combines the mapping (id/name/highalch) with latest (high
    price) into upsert-ready dicts."""
    prices = latest.get("data", {})
    result = []
    for entry in mapping:
        item_id = entry.get("id")
        if item_id is None:
            continue
        price_entry = prices.get(str(item_id)) or {}
        result.append(
            {
                "id": item_id,
                "name": entry.get("name", ""),
                "ge_price": price_entry.get("high"),
                "ha_value": entry.get("highalch"),
            }
        )
    return result


async def fetch_and_merge() -> list[dict]:
    async with httpx.AsyncClient(
        timeout=30.0, headers={"User-Agent": settings.user_agent}
    ) as client:
        mapping_response = await client.get(MAPPING_URL)
        mapping_response.raise_for_status()
        latest_response = await client.get(LATEST_URL)
        latest_response.raise_for_status()
    return merge_item_data(mapping_response.json(), latest_response.json())


async def run_once() -> None:
    items = await fetch_and_merge()
    now = dt.datetime.utcnow()
    async with async_session_factory() as session:
        for entry in items:
            result = await session.execute(select(Item).where(Item.id == entry["id"]))
            existing = result.scalar_one_or_none()
            if existing is None:
                session.add(
                    Item(
                        id=entry["id"],
                        name=entry["name"],
                        ge_price=entry["ge_price"],
                        ha_value=entry["ha_value"],
                        updated_at=now,
                    )
                )
            else:
                existing.name = entry["name"]
                existing.ge_price = entry["ge_price"]
                existing.ha_value = entry["ha_value"]
                existing.updated_at = now
        await session.commit()


async def items_worker_loop() -> None:
    while True:
        try:
            await run_once()
        except Exception:
            logger.exception("items worker iteration failed")
        await asyncio.sleep(POLL_INTERVAL_S)
