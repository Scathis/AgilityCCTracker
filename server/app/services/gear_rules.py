"""Runner gear rules + the internal "Running" anchor loadout.

Rules (owner-set 2026-10-27, editable on the dashboard Kits page): every
slot in the group's required slots must have something worn; and, if the
ranged-weapon rule is on, the weapon must be a bow or crossbow, with a
crossbow (one-handed) also needing a shield. Evaluation itself happens in
the plugin (GearRuleEvaluator); the server just stores and serves the rules.
"""

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.fc_group import FcGroup
from app.models.loadout import Loadout, LoadoutContext

# Fixed render order matching the equipment-tab grid layout.
SLOT_ORDER = [
    "HEAD",
    "CAPE",
    "AMULET",
    "AMMO",
    "WEAPON",
    "TORSO",
    "SHIELD",
    "LEGS",
    "HANDS",
    "BOOTS",
    "RING",
]

SLOT_LABELS = {
    "HEAD": "Helm",
    "CAPE": "Cape",
    "AMULET": "Neck",
    "AMMO": "Ammo",
    "WEAPON": "Weapon",
    "TORSO": "Body",
    "SHIELD": "Shield",
    "LEGS": "Legs",
    "HANDS": "Gloves",
    "BOOTS": "Boots",
    "RING": "Ring",
}

# Weapon/shield are covered by the ranged-weapon rule, not "must not be empty".
EMPTY_CHECK_SLOTS = [s for s in SLOT_ORDER if s not in ("WEAPON", "SHIELD")]

DEFAULT_REQUIRED_SLOTS = ["HEAD", "AMULET", "TORSO", "LEGS", "BOOTS"]


def required_slots(group: FcGroup) -> list[str]:
    slots = group.required_slots if group.required_slots is not None else DEFAULT_REQUIRED_SLOTS
    return [s for s in SLOT_ORDER if s in slots]


async def running_anchor(session: AsyncSession, fc_group_id: int) -> Loadout:
    """The group's "Running" loadout row that gear checks are stored against
    (created on demand, so a fresh group never lacks one)."""
    result = await session.execute(
        select(Loadout).where(
            Loadout.fc_group_id == fc_group_id, Loadout.context == LoadoutContext.RUNNING
        )
    )
    loadout = result.scalars().first()
    if loadout is None:
        loadout = Loadout(
            fc_group_id=fc_group_id, name="Running", context=LoadoutContext.RUNNING, enabled=True
        )
        session.add(loadout)
        await session.flush()
    return loadout
