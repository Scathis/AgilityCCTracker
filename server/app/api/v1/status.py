import time

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.deps import get_current_fc_group
from app.models.event import Attendance, Event
from app.models.fc_group import FcGroup
from app.models.kit import INVENTORY, Kit, KitItem
from app.models.player import Player
from app.schemas.status import (
    ActiveEventOut,
    DdConfigOut,
    DdTileOut,
    GearRulesOut,
    KitEquipmentOut,
    KitInventoryOut,
    KitOut,
    ParticipantOut,
    StatusOut,
)
from app.services.gear_rules import required_slots
from app.services.participation import participants_by_rsn

router = APIRouter()


@router.get("/status", response_model=StatusOut)
async def get_status(
    rsn: str | None = Query(default=None, max_length=12),
    fc_group: FcGroup = Depends(get_current_fc_group),
    session: AsyncSession = Depends(get_session),
) -> StatusOut:
    event_result = await session.execute(
        select(Event)
        .where(Event.fc_group_id == fc_group.id, Event.ended_at.is_(None))
        .order_by(Event.started_at.desc())
    )
    active_event = event_result.scalars().first()

    kit_rows = (
        (
            await session.execute(
                select(Kit).where(Kit.fc_group_id == fc_group.id).order_by(Kit.name)
            )
        )
        .scalars()
        .all()
    )
    kit_outs: list[KitOut] = []
    for kit in kit_rows:
        items = (
            (
                await session.execute(
                    select(KitItem).where(KitItem.kit_id == kit.id).order_by(KitItem.id)
                )
            )
            .scalars()
            .all()
        )
        kit_outs.append(
            KitOut(
                id=kit.id,
                name=kit.name,
                is_default=kit.is_default,
                equipment=[
                    KitEquipmentOut(slot=i.slot, item_id=i.item_id)
                    for i in items
                    if i.slot != INVENTORY
                ],
                inventory=[
                    KitInventoryOut(item_id=i.item_id, qty=i.qty)
                    for i in items
                    if i.slot == INVENTORY
                ],
            )
        )

    # Who's attending as what is only handed to viewers entitled to see gear
    # flags (their FC rank, as last reported by roster snapshots from *other*
    # plugin users, is at or above the group's threshold). Everyone else gets
    # just their own entry, which the plugin needs to restore its own answer.
    participants: list[ParticipantOut] = []
    if active_event is not None:
        entitled = await _viewer_entitled(session, active_event.id, rsn, fc_group)
        for participant_rsn, participant in (
            await participants_by_rsn(session, active_event.id)
        ).items():
            if entitled or (rsn is not None and participant_rsn.lower() == rsn.lower()):
                participants.append(
                    ParticipantOut(
                        rsn=participant_rsn,
                        status=participant.status.name,
                        role=participant.role.name,
                    )
                )

    tiles = [DdTileOut(x=t["x"], y=t["y"], plane=t["plane"]) for t in (fc_group.dd_tiles or [])]

    return StatusOut(
        fc_owner=fc_group.owner_rsn,
        fc_name=fc_group.name,
        server_time=int(time.time() * 1000),
        observer_enabled=fc_group.observer_enabled,
        flag_viewer_min_rank=fc_group.flag_viewer_min_rank,
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
        gear_rules=GearRulesOut(
            required_slots=required_slots(fc_group),
            ranged_weapon=fc_group.require_ranged_weapon,
        ),
        kits=kit_outs,
        dd=DdConfigOut(
            tiles=tiles,
            call_regex=fc_group.dd_call_regex or r"^\s*dd\s*!*$",
            break_regex=fc_group.dd_break_regex or r"^(?=.*\b(?:break|brk)\b)(?=.*\bdd\b).*$",
            min_rank=fc_group.dd_min_rank,
            arrive_grace_s=fc_group.dd_arrive_grace_s,
            leave_grace_s=fc_group.dd_leave_grace_s,
            max_minutes=fc_group.dd_max_minutes,
        ),
    )


async def _viewer_entitled(
    session: AsyncSession, event_id: int, rsn: str | None, fc_group: FcGroup
) -> bool:
    """True if `rsn`'s currently-open attendance row (rank reported by
    *roster snapshots*, not by the asker) meets the group's flag threshold."""
    if rsn is None:
        return False
    result = await session.execute(
        select(Attendance.rank)
        .join(Player, Attendance.player_id == Player.id)
        .where(
            Attendance.event_id == event_id,
            Attendance.left_at.is_(None),
            func.lower(Player.rsn) == rsn.lower(),
        )
        .order_by(Attendance.joined_at.desc())
    )
    rank = result.scalars().first()
    return rank is not None and rank >= fc_group.flag_viewer_min_rank
