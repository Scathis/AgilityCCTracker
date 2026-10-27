import datetime as dt

from fastapi import APIRouter, Depends, Form, HTTPException, Request
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.dd import DdCall, DdPresence
from app.models.death import Death
from app.models.event import Attendance, Event, ParticipantStint
from app.models.fc_group import FcGroup
from app.models.lap import Lap
from app.models.loadout import Loadout, LoadoutCheck, LoadoutCheckSource, LoadoutContext
from app.models.player import Player
from app.models.ragger import RaggerSighting
from app.models.user import User, UserRole
from app.services.participation import participants_by_rsn
from app.web.deps import (
    get_default_fc_group,
    require_role,
    require_user,
    template_context,
    verify_csrf,
)
from app.web.templating import templates

router = APIRouter()


@router.get("/events")
async def list_events(
    request: Request,
    user: User = Depends(require_user),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    result = await session.execute(
        select(Event).where(Event.fc_group_id == group.id).order_by(Event.started_at.desc())
    )
    events = result.scalars().all()
    active_event = next((e for e in events if e.ended_at is None), None)
    context = template_context(request, user, events=events, active_event=active_event)
    return templates.TemplateResponse(request, "events/list.html", context)


@router.post("/events")
async def start_event(
    request: Request,
    name: str = Form(...),
    notes: str = Form(""),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    existing = await session.execute(
        select(Event).where(Event.fc_group_id == group.id, Event.ended_at.is_(None))
    )
    if existing.scalars().first() is not None:
        raise HTTPException(status_code=400, detail="An event is already active")

    event = Event(
        fc_group_id=group.id,
        name=name,
        started_at=dt.datetime.utcnow(),
        started_by_user_id=user.id,
        notes=notes or None,
    )
    session.add(event)
    await session.commit()
    return RedirectResponse(url="/events", status_code=303)


@router.post("/events/{event_id}/stop")
async def stop_event(
    event_id: int,
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    result = await session.execute(select(Event).where(Event.id == event_id))
    event = result.scalar_one_or_none()
    if event is None:
        raise HTTPException(status_code=404, detail="Event not found")
    if event.ended_at is None:
        event.ended_at = dt.datetime.utcnow()
        await session.commit()
    return RedirectResponse(url="/events", status_code=303)


@router.get("/events/{event_id}")
async def event_detail(
    event_id: int,
    request: Request,
    tab: str = "attendance",
    user: User = Depends(require_user),
    group: FcGroup = Depends(get_default_fc_group),
    session: AsyncSession = Depends(get_session),
):
    result = await session.execute(
        select(Event).where(Event.id == event_id, Event.fc_group_id == group.id)
    )
    event = result.scalar_one_or_none()
    if event is None:
        raise HTTPException(status_code=404, detail="Event not found")

    context = template_context(request, user, event=event, tab=tab)

    if tab == "attendance":
        context.update(await _attendance_tab(session, group, event))
    elif tab == "laps":
        context.update(await _laps_tab(session, event))
    elif tab == "dd":
        context.update(await _dd_tab(session, event))
    elif tab == "deaths":
        context.update(await _deaths_tab(session, event))
    elif tab == "manual-gap":
        context.update(await _manual_gap_tab(session, group, event))

    # The attendance/laps/DD tabs poll themselves via HTMX while an event is
    # active (see detail.html) - on a poll, return just that tab's fragment
    # instead of the whole page (which would otherwise nest a full page
    # inside the polled container).
    if request.headers.get("HX-Request") == "true" and tab in ("attendance", "laps", "dd"):
        return templates.TemplateResponse(request, f"events/_{tab}.html", context)

    return templates.TemplateResponse(request, "events/detail.html", context)


async def _attendance_tab(session: AsyncSession, group: FcGroup, event: Event) -> dict:
    rows = (
        await session.execute(
            select(Attendance, Player)
            .join(Player, Attendance.player_id == Player.id)
            .where(Attendance.event_id == event.id)
        )
    ).all()

    running = (
        await session.execute(
            select(Loadout).where(
                Loadout.fc_group_id == group.id, Loadout.context == LoadoutContext.RUNNING
            )
        )
    ).scalar_one_or_none()

    now = dt.datetime.utcnow()
    participants = await participants_by_rsn(session, event.id)
    event_end = event.ended_at or now

    stint_result = await session.execute(
        select(ParticipantStint).where(ParticipantStint.event_id == event.id)
    )
    stint_rows = stint_result.scalars().all()
    stints_by_player: dict[int, list[ParticipantStint]] = {}
    for stint in sorted(stint_rows, key=lambda st: st.started_at):
        stints_by_player.setdefault(stint.player_id, []).append(stint)

    # Per player: every attendance session (joined/left/rejoined), plus rank
    # and whether they're currently present.
    base: dict[int, dict] = {}
    for attendance, player in rows:
        info = base.setdefault(
            player.id,
            {
                "player": player,
                "rank": attendance.rank,
                "sessions": [],
                "currently_present": False,
                "last_left_at": None,
            },
        )
        info["sessions"].append((attendance.joined_at, attendance.left_at or now))
        if attendance.left_at is None:
            info["currently_present"] = True
        elif info["last_left_at"] is None or attendance.left_at > info["last_left_at"]:
            info["last_left_at"] = attendance.left_at

    def overlap_seconds(sessions, start, end) -> int:
        total = 0
        for joined, left in sessions:
            lo, hi = max(joined, start), min(left, end)
            if hi > lo:
                total += int((hi - lo).total_seconds())
        return total

    # One row per (player, role): a player who was Anti for a while and then
    # Runner (or the other way round) gets two rows with separate time/laps -
    # never combined. Players with no plugin answer get a single plain row.
    entries: list[dict] = []
    for player_id, info in base.items():
        player = info["player"]
        common = {
            "player": player,
            "rank": info["rank"],
            "currently_present": info["currently_present"],
            "last_left_at": info["last_left_at"],
            "loadout_gaps": None,
            "laps": 0,
        }
        stints = stints_by_player.get(player_id, [])
        if not stints:
            participation = participants.get(player.rsn)
            declined = participation is not None and participation.status.value == "declined"
            entries.append(
                {
                    **common,
                    "role_label": "Declined" if declined else "Runner",
                    "role_note": "" if declined else "(no plugin answer)",
                    "is_anti": False,
                    "seconds_present": sum(
                        int((left - joined).total_seconds()) for joined, left in info["sessions"]
                    ),
                    "windows": None,
                }
            )
            continue

        # Time present before their first answer belongs to the role they chose.
        earliest_join = min(joined for joined, _ in info["sessions"])
        by_role: dict[str, dict] = {}
        for index, stint in enumerate(stints):
            start = min(stint.started_at, earliest_join) if index == 0 else stint.started_at
            end = stint.ended_at or event_end
            role_entry = by_role.setdefault(
                stint.role.value, {"seconds": 0, "windows": [], "role": stint.role}
            )
            role_entry["seconds"] += overlap_seconds(info["sessions"], start, end)
            role_entry["windows"].append((start, end))
        for role_entry in by_role.values():
            is_anti = role_entry["role"].value == "anti"
            entries.append(
                {
                    **common,
                    "role_label": "Anti" if is_anti else "Runner",
                    "role_note": "",
                    "is_anti": is_anti,
                    "seconds_present": role_entry["seconds"],
                    "windows": role_entry["windows"],
                }
            )

    latest_check_by_player: dict[int, list | None] = {}
    if running is not None:
        for player_id in base:
            check_result = await session.execute(
                select(LoadoutCheck)
                .where(
                    LoadoutCheck.player_id == player_id,
                    LoadoutCheck.event_id == event.id,
                    LoadoutCheck.loadout_id == running.id,
                )
                .order_by(LoadoutCheck.checked_at.desc())
            )
            latest = check_result.scalars().first()
            latest_check_by_player[player_id] = (
                latest.failed_requirement_keys if latest is not None else None
            )

    laps_by_player: dict[int, list[dt.datetime]] = {}
    for lap in (
        (await session.execute(select(Lap).where(Lap.event_id == event.id))).scalars().all()
    ):
        laps_by_player.setdefault(lap.player_id, []).append(lap.completed_at)

    for entry in entries:
        player_id = entry["player"].id
        entry["loadout_gaps"] = latest_check_by_player.get(player_id)
        laps = laps_by_player.get(player_id, [])
        if entry["windows"] is None:
            entry["laps"] = len(laps)
        else:
            entry["laps"] = sum(1 for t in laps if any(a <= t <= b for a, b in entry["windows"]))

    return {
        "attendees": sorted(entries, key=lambda e: (e["player"].rsn.lower(), e["role_label"]))
    }


async def _laps_tab(session: AsyncSession, event: Event) -> dict:
    rows = (
        await session.execute(
            select(Lap, Player)
            .join(Player, Lap.player_id == Player.id)
            .where(Lap.event_id == event.id)
        )
    ).all()
    totals: dict[int, dict] = {}
    for lap, player in rows:
        entry = totals.setdefault(player.id, {"player": player, "laps": 0, "tickets": 0})
        entry["laps"] += 1
        entry["tickets"] += lap.tickets_gained or 0
    leaderboard = sorted(totals.values(), key=lambda e: e["laps"], reverse=True)
    return {"leaderboard": leaderboard}


async def _dd_tab(session: AsyncSession, event: Event) -> dict:
    result = await session.execute(
        select(DdCall).where(DdCall.event_id == event.id).order_by(DdCall.called_at.desc())
    )
    calls = result.scalars().all()
    call_summaries = []
    for call in calls:
        presence_result = await session.execute(
            select(DdPresence).where(DdPresence.dd_call_id == call.id)
        )
        presences = presence_result.scalars().all()
        rsns = {}
        for p in presences:
            player_result = await session.execute(select(Player).where(Player.id == p.player_id))
            player = player_result.scalar_one_or_none()
            rsns[p.id] = player.rsn if player else "?"
        call_summaries.append({"call": call, "presence": presences, "rsns": rsns})
    return {"call_summaries": call_summaries}


async def _deaths_tab(session: AsyncSession, event: Event) -> dict:
    death_result = await session.execute(
        select(Death, Player)
        .join(Player, Death.player_id == Player.id)
        .where(Death.event_id == event.id)
    )
    sighting_result = await session.execute(
        select(RaggerSighting).where(RaggerSighting.event_id == event.id)
    )
    return {"deaths": death_result.all(), "sightings": sighting_result.scalars().all()}


async def _manual_gap_tab(session: AsyncSession, group: FcGroup, event: Event) -> dict:
    attendee_result = await session.execute(
        select(Player)
        .join(Attendance, Attendance.player_id == Player.id)
        .where(Attendance.event_id == event.id)
        .distinct()
    )
    loadout_result = await session.execute(select(Loadout).where(Loadout.fc_group_id == group.id))
    return {
        "attendees_for_gap": attendee_result.scalars().all(),
        "loadouts": loadout_result.scalars().all(),
    }


@router.post("/events/{event_id}/manual-gap")
async def add_manual_gap(
    event_id: int,
    player_id: int = Form(...),
    loadout_id: int = Form(...),
    failed: str = Form(""),
    notes: str = Form(""),
    session: AsyncSession = Depends(get_session),
    user: User = Depends(require_role(UserRole.STAFF)),
    _csrf: User = Depends(verify_csrf),
):
    failed_keys = [k.strip() for k in failed.split(",") if k.strip()]
    session.add(
        LoadoutCheck(
            player_id=player_id,
            event_id=event_id,
            loadout_id=loadout_id,
            checked_at=dt.datetime.utcnow(),
            failed_requirement_keys=failed_keys,
            source=LoadoutCheckSource.STAFF_MANUAL,
            reported_by_user_id=user.id,
            notes=notes or None,
        )
    )
    await session.commit()
    return RedirectResponse(url=f"/events/{event_id}?tab=manual-gap", status_code=303)
