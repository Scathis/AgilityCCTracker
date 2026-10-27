import time

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.deps import get_current_fc_group
from app.models.event import Event
from app.models.fc_group import FcGroup
from app.models.loadout import Loadout, LoadoutRequirement
from app.schemas.status import (
    ActiveEventOut,
    DdConfigOut,
    DdTileOut,
    LoadoutOut,
    ParticipantOut,
    RequirementOut,
    StatusOut,
)
from app.services.loadouts import serialize_loadout
from app.services.participation import participants_by_rsn

router = APIRouter()


@router.get("/status", response_model=StatusOut)
async def get_status(
    fc_group: FcGroup = Depends(get_current_fc_group),
    session: AsyncSession = Depends(get_session),
) -> StatusOut:
    event_result = await session.execute(
        select(Event)
        .where(Event.fc_group_id == fc_group.id, Event.ended_at.is_(None))
        .order_by(Event.started_at.desc())
    )
    active_event = event_result.scalars().first()

    loadouts_result = await session.execute(
        select(Loadout).where(Loadout.fc_group_id == fc_group.id, Loadout.enabled.is_(True))
    )
    loadouts = loadouts_result.scalars().all()

    loadout_outs: list[LoadoutOut] = []
    for loadout in loadouts:
        req_result = await session.execute(
            select(LoadoutRequirement).where(LoadoutRequirement.loadout_id == loadout.id)
        )
        requirements = req_result.scalars().all()
        loadout_out = serialize_loadout(loadout, requirements)
        if loadout.context.value == "dd" and not fc_group.dd_crossbow_counts_as_bow:
            # BUILD_PLAN.md §7: server-added, not stored - a real requirement
            # row would need updating every time this per-group toggle flips.
            loadout_out.requirements.append(
                RequirementOut(
                    id="dd-no-xbow",
                    label="Crossbow at DD",
                    slot="WEAPON",
                    check="NAME_NOT_ENDS_WITH",
                    args=["crossbow"],
                    when=None,
                )
            )
        loadout_outs.append(loadout_out)

    participants: list[ParticipantOut] = []
    if active_event is not None:
        for rsn, participant in (await participants_by_rsn(session, active_event.id)).items():
            participants.append(
                ParticipantOut(rsn=rsn, status=participant.status.name, role=participant.role.name)
            )

    tiles = [DdTileOut(x=t["x"], y=t["y"], plane=t["plane"]) for t in (fc_group.dd_tiles or [])]

    return StatusOut(
        fc_owner=fc_group.owner_rsn,
        fc_name=fc_group.name,
        server_time=int(time.time() * 1000),
        observer_enabled=fc_group.observer_enabled,
        active_event=(
            ActiveEventOut(
                id=active_event.id,
                name=active_event.name,
                started_at=int(active_event.started_at.timestamp() * 1000),
            )
            if active_event
            else None
        ),
        participants=participants,
        loadouts=loadout_outs,
        dd=DdConfigOut(
            tiles=tiles,
            call_regex=fc_group.dd_call_regex or r"^\s*dd\s*!*$",
            break_regex=fc_group.dd_break_regex or r"^(?=.*\b(?:break|brk)\b)(?=.*\bdd\b).*$",
            min_rank=fc_group.dd_min_rank,
            arrive_grace_s=fc_group.dd_arrive_grace_s,
            leave_grace_s=fc_group.dd_leave_grace_s,
            max_minutes=fc_group.dd_max_minutes,
            crossbow_counts_as_bow_at_dd=fc_group.dd_crossbow_counts_as_bow,
        ),
    )
