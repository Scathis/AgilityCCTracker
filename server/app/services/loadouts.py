"""Serialises Loadout/LoadoutRequirement DB rows into the wire shape the
plugin expects (BUILD_PLAN.md §5.6's StatusResponse.loadouts). Kept as one
choke point specifically for the lowercase-DB / uppercase-wire `context`
mapping described in app/schemas/status.py - never done inline at the route.
"""

from app.models.loadout import Loadout, LoadoutRequirement
from app.schemas.status import LoadoutOut, RequirementOut

# Fixed render order matching the equipment-tab grid layout, not a
# per-requirement "sort" field - slots have one natural position each.
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


def serialize_loadout(loadout: Loadout, requirements: list[LoadoutRequirement]) -> LoadoutOut:
    by_slot = {req.slot: req for req in requirements}
    ordered = [by_slot[slot] for slot in SLOT_ORDER if slot in by_slot]
    return LoadoutOut(
        id=loadout.id,
        name=loadout.name,
        context=loadout.context.value.upper(),
        requirements=[
            RequirementOut(
                slot=req.slot,
                required=req.required,
                allowed_item_ids=req.allowed_item_ids or [],
            )
            for req in ordered
        ],
    )
