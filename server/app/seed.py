"""Anchor loadout rows for a new FC group. Since 2026-10-27 these carry no
rules at all - gear checks are stored against the "Running" row, the rules
live on FcGroup (required_slots / require_ranged_weapon) and recommended
setups are Kits. The "DD" row is a leftover kept only for the return shape.
"""

from sqlalchemy.ext.asyncio import AsyncSession

from app.models.loadout import Loadout, LoadoutContext


async def seed_default_loadouts(session: AsyncSession, fc_group_id: int) -> tuple[Loadout, Loadout]:
    running = Loadout(
        fc_group_id=fc_group_id, name="Running", context=LoadoutContext.RUNNING, enabled=True
    )
    dd = Loadout(fc_group_id=fc_group_id, name="DD", context=LoadoutContext.DD, enabled=True)
    session.add_all([running, dd])
    await session.flush()
    return running, dd
