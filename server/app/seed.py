"""Default loadouts (BUILD_PLAN.md §7), seeded per FC group at group-creation
time (wired into the CLI in a later phase). Slot/check values are stored
exactly as the plugin's wire format expects (uppercase) - see
app/schemas/status.py's note on why context is the one field that needs a
DB<->wire case mapping and these don't.
"""

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.loadout import Loadout, LoadoutContext, LoadoutRequirement

RUNNING_REQUIREMENTS = [
    dict(
        req_key="run-weapon",
        label="No weapon in hand",
        slot="WEAPON",
        check_type="FILLED",
        args=[],
        when_json=None,
        sort=0,
    ),
    dict(
        req_key="run-bow",
        label="Weapon isn't a bow",
        slot="WEAPON",
        check_type="NAME_ENDS_WITH",
        args=["bow"],
        when_json=None,
        sort=1,
    ),
    dict(
        req_key="run-helm",
        label="Missing helm",
        slot="HEAD",
        check_type="FILLED",
        args=[],
        when_json=None,
        sort=2,
    ),
    dict(
        req_key="run-legs",
        label="Missing legs",
        slot="LEGS",
        check_type="FILLED",
        args=[],
        when_json=None,
        sort=3,
    ),
    dict(
        req_key="run-boots",
        label="Missing boots",
        slot="BOOTS",
        check_type="FILLED",
        args=[],
        when_json=None,
        sort=4,
    ),
    dict(
        req_key="run-neck",
        label="Missing necklace",
        slot="AMULET",
        check_type="FILLED",
        args=[],
        when_json=None,
        sort=5,
    ),
    dict(
        req_key="run-xbow-shield",
        label="Crossbow without shield",
        slot="SHIELD",
        check_type="FILLED",
        args=[],
        when_json={"slot": "WEAPON", "check": "NAME_ENDS_WITH", "args": ["crossbow"]},
        sort=6,
    ),
]

DD_REQUIREMENTS = [
    dict(
        req_key="dd-bow",
        label="Not holding a bow at DD",
        slot="WEAPON",
        check_type="NAME_ENDS_WITH",
        args=["bow"],
        when_json=None,
        sort=0,
    ),
]


async def seed_default_loadouts(session: AsyncSession, fc_group_id: int) -> tuple[Loadout, Loadout]:
    running = Loadout(
        fc_group_id=fc_group_id, name="Running", context=LoadoutContext.RUNNING, enabled=True
    )
    dd = Loadout(fc_group_id=fc_group_id, name="DD", context=LoadoutContext.DD, enabled=True)
    session.add_all([running, dd])
    await session.flush()

    for req in RUNNING_REQUIREMENTS:
        session.add(LoadoutRequirement(loadout_id=running.id, **req))
    for req in DD_REQUIREMENTS:
        session.add(LoadoutRequirement(loadout_id=dd.id, **req))

    await session.flush()
    return running, dd
