"""Hiscores background worker - BUILD_PLAN.md §6.6.

Every minute: queue non-plugin players who attended a recent event (last 30
days - the spec says "recent events" without an exact window; 30 days is a
disclosed judgment call, not a re-derivable constant) and raggers, whichever
haven't been refreshed in the last 24h. Fetch from the OSRS hiscores,
<=1 request/2s, 404 handled gracefully.

Parsing + the combat-level formula are pure functions, deliberately separate
from the network fetch, so they're unit-testable without hitting the real
hiscores API.
"""

import asyncio
import datetime as dt
import logging
import math

import httpx
from sqlalchemy import select

from app.config import settings
from app.db import async_session_factory
from app.models.event import Attendance, Event
from app.models.player import Player, PlayerSnapshot, SnapshotSource
from app.models.ragger import Ragger

logger = logging.getLogger(__name__)

HISCORES_URL = "https://secure.runescape.com/m=hiscore_oldschool/index_lite.json"
STALE_AFTER = dt.timedelta(hours=24)
RECENT_EVENT_WINDOW = dt.timedelta(days=30)
REQUEST_INTERVAL_S = 2.0
POLL_INTERVAL_S = 60.0

SKILL_NAMES = [
    "overall",
    "attack",
    "defence",
    "strength",
    "hitpoints",
    "ranged",
    "prayer",
    "magic",
    "cooking",
    "woodcutting",
    "fletching",
    "fishing",
    "firemaking",
    "crafting",
    "smithing",
    "mining",
    "herblore",
    "agility",
    "thieving",
    "slayer",
    "farming",
    "runecraft",
    "hunter",
    "construction",
    "sailing",
]


def parse_hiscores_json(data: dict) -> dict[str, dict[str, int]]:
    """skill name -> {level, xp}, from the index_lite.json "skills" array."""
    skills: dict[str, dict[str, int]] = {}
    for entry in data.get("skills", []):
        skill_id = entry.get("id")
        name = (
            SKILL_NAMES[skill_id]
            if skill_id is not None and skill_id < len(SKILL_NAMES)
            else str(skill_id)
        )
        skills[name] = {"level": entry.get("level", 1), "xp": entry.get("xp", -1)}
    return skills


def compute_combat_level(skills: dict[str, dict[str, int]]) -> int:
    """BUILD_PLAN.md §6.6's formula, verbatim."""

    def lvl(name: str) -> int:
        return skills.get(name, {}).get("level", 1)

    base = 0.25 * (lvl("defence") + lvl("hitpoints") + math.floor(lvl("prayer") / 2))
    melee = 0.325 * (lvl("attack") + lvl("strength"))
    ranged = 0.325 * math.floor(lvl("ranged") * 1.5)
    mage = 0.325 * math.floor(lvl("magic") * 1.5)
    return math.floor(base + max(melee, ranged, mage))


async def fetch_hiscores(client: httpx.AsyncClient, rsn: str) -> dict | None:
    """None on a 404 (unranked/renamed) or any other error - handled gracefully, per §6.6."""
    try:
        response = await client.get(
            HISCORES_URL, params={"player": rsn}, headers={"User-Agent": settings.user_agent}
        )
        if response.status_code == 404:
            return None
        response.raise_for_status()
        return response.json()
    except httpx.HTTPError:
        logger.warning("Hiscores fetch failed for %s", rsn, exc_info=True)
        return None


async def _queue() -> list[tuple[str, str]]:
    """Returns (kind, rsn) pairs - kind is "player" or "ragger"."""
    stale_cutoff = dt.datetime.utcnow() - STALE_AFTER
    recent_cutoff = dt.datetime.utcnow() - RECENT_EVENT_WINDOW
    async with async_session_factory() as session:
        player_result = await session.execute(
            select(Player)
            .join(Attendance, Attendance.player_id == Player.id)
            .join(Event, Event.id == Attendance.event_id)
            .where(Player.is_plugin_user.is_(False))
            .where(Event.started_at >= recent_cutoff)
            .where(
                (Player.hiscores_updated_at.is_(None)) | (Player.hiscores_updated_at < stale_cutoff)
            )
            .distinct()
        )
        ragger_result = await session.execute(
            select(Ragger).where(
                (Ragger.hiscores_updated_at.is_(None)) | (Ragger.hiscores_updated_at < stale_cutoff)
            )
        )
        players = [("player", p.rsn) for p in player_result.scalars().all()]
        raggers = [("ragger", r.rsn) for r in ragger_result.scalars().all()]
    return players + raggers


async def _store_result(kind: str, rsn: str, data: dict | None) -> None:
    now = dt.datetime.utcnow()
    async with async_session_factory() as session:
        if kind == "player":
            result = await session.execute(select(Player).where(Player.rsn == rsn))
            player = result.scalar_one_or_none()
            if player is None:
                return
            player.hiscores_updated_at = now
            if data is not None:
                skills = parse_hiscores_json(data)
                snapshot = PlayerSnapshot(
                    player_id=player.id,
                    captured_at=now,
                    source=SnapshotSource.HISCORES,
                    combat_level=compute_combat_level(skills),
                    skills=skills,
                    equipment=None,
                )
                session.add(snapshot)
                await session.flush()
                player.latest_snapshot_id = snapshot.id
        else:
            result = await session.execute(select(Ragger).where(Ragger.rsn == rsn))
            ragger = result.scalar_one_or_none()
            if ragger is None:
                return
            ragger.hiscores_updated_at = now
            if data is not None:
                skills = parse_hiscores_json(data)
                ragger.combat_level = compute_combat_level(skills)
                ragger.skills = skills
        await session.commit()


async def run_once() -> None:
    queue = await _queue()
    if not queue:
        return
    async with httpx.AsyncClient(timeout=10.0) as client:
        for kind, rsn in queue:
            data = await fetch_hiscores(client, rsn)
            await _store_result(kind, rsn, data)
            await asyncio.sleep(REQUEST_INTERVAL_S)


async def hiscores_worker_loop() -> None:
    while True:
        try:
            await run_once()
        except Exception:
            logger.exception("hiscores worker iteration failed")
        await asyncio.sleep(POLL_INTERVAL_S)
