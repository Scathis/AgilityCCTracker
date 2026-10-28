from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.fc_group import FcGroup
from app.models.user import User, UserRole
from app.services.gear_rules import EMPTY_CHECK_SLOTS, SLOT_LABELS, required_slots
from app.web.deps import get_default_fc_group, require_role, template_context, verify_csrf
from app.web.templating import templates

router = APIRouter()


@router.get("/settings")
async def settings_page(
    request: Request,
    user: User = Depends(require_role(UserRole.ADMIN)),
    group: FcGroup = Depends(get_default_fc_group),
):
    tiles = group.dd_tiles or [{"x": 0, "y": 0, "plane": 0}, {"x": 0, "y": 0, "plane": 0}]
    current = set(required_slots(group))
    rule_slots = [(s, SLOT_LABELS[s], s in current) for s in EMPTY_CHECK_SLOTS]
    context = template_context(request, user, group=group, tiles=tiles, rule_slots=rule_slots)
    return templates.TemplateResponse(request, "settings.html", context)


@router.post("/settings")
async def update_settings(
    min_rank: int = Form(...),
    arrive_grace_s: int = Form(...),
    leave_grace_s: int = Form(...),
    max_minutes: int = Form(...),
    observer_enabled: bool = Form(False),
    flag_viewer_min_rank: int = Form(0),
    required: list[str] = Form(default=[]),
    ranged_weapon: bool = Form(False),
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.ADMIN)),
    _csrf: User = Depends(verify_csrf),
):
    # DD tiles are deliberately not settable from this form - see settings.html.
    # The DD call/break patterns live in code (api/v1/status.py).
    group.dd_min_rank = max(-1, min(min_rank, 127))
    group.dd_arrive_grace_s = arrive_grace_s
    group.dd_leave_grace_s = leave_grace_s
    group.dd_max_minutes = max_minutes
    group.observer_enabled = observer_enabled
    group.flag_viewer_min_rank = max(-1, min(flag_viewer_min_rank, 127))
    # Runner gear rules. WEAPON/SHIELD are covered by the ranged-weapon rule,
    # never as plain "must not be empty" slots.
    group.required_slots = [s for s in EMPTY_CHECK_SLOTS if s in required]
    group.require_ranged_weapon = ranged_weapon
    await session.commit()
    return RedirectResponse(url="/settings?saved=1", status_code=303)
