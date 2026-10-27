"""Items background worker. Daily: fetch RuneLite's item-name dump (every
item in the game cache, id -> name) and its item-stats dump (which slot each
wearable item goes in) - both refreshed by RuneLite on each game update -
and upsert them into `items`, which the Kits item search runs against.

Switched 2026-10-28 from the OSRS Wiki's prices "mapping" endpoint, which
only lists Grand Exchange-tradeable items - so untradeable runner gear (Ava's
devices, fire cape, graceful, defenders, imbued rings...) couldn't be picked
in a kit. Names only: prices are deliberately never fetched or stored (owner
request 2026-10-01 - a gear/risk-value calculator reads as a tool for
spotting high-value PK targets).
"""

import asyncio
import datetime as dt
import logging

import httpx
from sqlalchemy.dialects.mysql import insert

from app.config import settings
from app.db import async_session_factory
from app.models.item import Item

logger = logging.getLogger(__name__)

NAMES_URL = "https://static.runelite.net/cache/item/names.json"
STATS_URL = "https://static.runelite.net/item/stats.ids.min.json"
# RuneLite's EquipmentInventorySlot index -> this project's slot names.
SLOT_BY_INDEX = {
    0: "HEAD",
    1: "CAPE",
    2: "AMULET",
    3: "WEAPON",
    4: "TORSO",
    5: "SHIELD",
    7: "LEGS",
    9: "HANDS",
    10: "BOOTS",
    12: "RING",
    13: "AMMO",
}
POLL_INTERVAL_S = 24 * 3600
BATCH = 1000


def parse_item_names(names: dict[str, str]) -> list[dict]:
    """Pure - {"4151": "Abyssal whip", ...} -> [{"id": 4151, "name": ...}],
    skipping the cache's unnamed placeholders ("null" / blank)."""
    result = []
    for raw_id, name in names.items():
        try:
            item_id = int(raw_id)
        except (TypeError, ValueError):
            continue
        name = (name or "").strip()
        if not name or name.lower() == "null":
            continue
        result.append({"id": item_id, "name": name[:255]})
    return result


def parse_equip_slots(stats: dict[str, dict]) -> dict[int, str]:
    """Pure - item id -> slot name, for wearable items with a known slot only."""
    slots = {}
    for raw_id, entry in stats.items():
        equipment = (entry or {}).get("equipment") or {}
        slot = SLOT_BY_INDEX.get(equipment.get("slot")) if entry.get("equipable") else None
        if slot is None:
            continue
        try:
            slots[int(raw_id)] = slot
        except (TypeError, ValueError):
            continue
    return slots


async def fetch_items() -> list[dict]:
    async with httpx.AsyncClient(
        timeout=60.0, headers={"User-Agent": settings.user_agent}
    ) as client:
        names = await client.get(NAMES_URL)
        names.raise_for_status()
        stats = await client.get(STATS_URL)
        stats.raise_for_status()
    slots = parse_equip_slots(stats.json())
    return [
        {**entry, "equip_slot": slots.get(entry["id"])} for entry in parse_item_names(names.json())
    ]


async def run_once() -> None:
    items = await fetch_items()
    now = dt.datetime.utcnow()
    async with async_session_factory() as session:
        for start in range(0, len(items), BATCH):
            rows = [{**entry, "updated_at": now} for entry in items[start : start + BATCH]]
            stmt = insert(Item).values(rows)
            stmt = stmt.on_duplicate_key_update(
                name=stmt.inserted.name, equip_slot=stmt.inserted.equip_slot, updated_at=now
            )
            await session.execute(stmt)
        await session.commit()
    logger.info("items worker: %d item names loaded", len(items))


async def items_worker_loop() -> None:
    while True:
        try:
            await run_once()
        except Exception:
            logger.exception("items worker iteration failed")
        await asyncio.sleep(POLL_INTERVAL_S)
