"""Default loadouts for a new FC group - just the two empty shells (Running,
DD). No default requirements are seeded: BUILD_PLAN.md §7's original
defaults were expressed as name-pattern/conditional rules that don't map
onto the per-slot allow-list model (see docs/BUILD_LOG.md's loadout-
redesign entry) - staff build the real rules themselves via the dashboard's
interactive slot-grid editor ("build a runner loadout").
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
