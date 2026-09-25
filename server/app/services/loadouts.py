"""Serialises Loadout/LoadoutRequirement DB rows into the wire shape the
plugin expects (BUILD_PLAN.md §5.6's StatusResponse.loadouts). Kept as one
choke point specifically for the lowercase-DB / uppercase-wire `context`
mapping described in app/schemas/status.py - never done inline at the route.
"""

from app.models.loadout import Loadout, LoadoutRequirement
from app.schemas.status import LoadoutOut, RequirementOut


def serialize_loadout(loadout: Loadout, requirements: list[LoadoutRequirement]) -> LoadoutOut:
    return LoadoutOut(
        id=loadout.id,
        name=loadout.name,
        context=loadout.context.value.upper(),
        requirements=[
            RequirementOut(
                id=req.req_key,
                label=req.label,
                slot=req.slot,
                check=req.check_type,
                args=req.args or [],
                when=req.when_json,
            )
            for req in sorted(requirements, key=lambda r: r.sort)
        ],
    )
