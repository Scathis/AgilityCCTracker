from fastapi import APIRouter, Depends, Form, Request
from fastapi.responses import RedirectResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.models.fc_group import FcGroup
from app.models.user import User, UserRole
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
    context = template_context(request, user, group=group, tiles=tiles)
    return templates.TemplateResponse(request, "settings.html", context)


@router.post("/settings")
async def update_settings(
    call_regex: str = Form(...),
    break_regex: str = Form(...),
    min_rank: int = Form(...),
    arrive_grace_s: int = Form(...),
    leave_grace_s: int = Form(...),
    max_minutes: int = Form(...),
    crossbow_counts_as_bow: bool = Form(False),
    observer_enabled: bool = Form(False),
    flag_viewer_min_rank: int = Form(0),
    session: AsyncSession = Depends(get_session),
    group: FcGroup = Depends(get_default_fc_group),
    user: User = Depends(require_role(UserRole.ADMIN)),
    _csrf: User = Depends(verify_csrf),
):
    # DD tiles are deliberately not settable from this form - see settings.html.
    group.dd_call_regex = call_regex
    group.dd_break_regex = break_regex
    group.dd_min_rank = min_rank
    group.dd_arrive_grace_s = arrive_grace_s
    group.dd_leave_grace_s = leave_grace_s
    group.dd_max_minutes = max_minutes
    group.dd_crossbow_counts_as_bow = crossbow_counts_as_bow
    group.observer_enabled = observer_enabled
    group.flag_viewer_min_rank = max(-1, min(flag_viewer_min_rank, 127))
    await session.commit()
    return RedirectResponse(url="/settings", status_code=303)
