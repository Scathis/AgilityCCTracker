import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Request
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from starlette.middleware.base import BaseHTTPMiddleware

from app.api.v1.ingest import router as ingest_router
from app.api.v1.participation import router as participation_router
from app.api.v1.status import router as status_router
from app.config import settings
from app.models.user import User
from app.web.deps import RequiresLogin, get_current_user
from app.web.routes.admin import router as admin_router
from app.web.routes.auth import router as auth_router
from app.web.routes.dd import router as dd_router
from app.web.routes.events import router as events_router
from app.web.routes.kits import router as kits_router
from app.web.routes.players import router as players_router
from app.web.routes.raggers import router as raggers_router
from app.web.routes.settings import router as settings_router
from app.workers.attendance_timeout_worker import attendance_timeout_worker_loop
from app.workers.hiscores_worker import hiscores_worker_loop
from app.workers.items_worker import items_worker_loop

logger = logging.getLogger(__name__)

MAX_BODY_BYTES = 100 * 1024


@asynccontextmanager
async def lifespan(app: FastAPI):
    tasks: list[asyncio.Task] = []
    tasks.append(asyncio.create_task(attendance_timeout_worker_loop()))
    if settings.hiscores_enabled:
        tasks.append(asyncio.create_task(hiscores_worker_loop()))
    if settings.items_enabled:
        tasks.append(asyncio.create_task(items_worker_loop()))
    logger.info("Started %d background worker(s)", len(tasks))
    yield
    for task in tasks:
        task.cancel()
    for task in tasks:
        try:
            await task
        except asyncio.CancelledError:
            pass


class BodySizeLimitMiddleware(BaseHTTPMiddleware):
    """Rejects oversized plugin request bodies with 413, per BUILD_PLAN.md §6.3."""

    async def dispatch(self, request: Request, call_next):
        content_length = request.headers.get("content-length")
        if content_length is not None and int(content_length) > MAX_BODY_BYTES:
            return JSONResponse(status_code=413, content={"detail": "Request body too large"})
        return await call_next(request)


app = FastAPI(title="Wildy Agility FC Tracker", lifespan=lifespan)
app.add_middleware(BodySizeLimitMiddleware)


@app.exception_handler(RequiresLogin)
async def handle_requires_login(request: Request, exc: RequiresLogin) -> RedirectResponse:
    return RedirectResponse(url="/login", status_code=303)


app.include_router(status_router, prefix="/api/v1")
app.include_router(ingest_router, prefix="/api/v1")
app.include_router(participation_router, prefix="/api/v1")
app.include_router(auth_router)
app.include_router(events_router)
app.include_router(dd_router)
app.include_router(kits_router)
app.include_router(players_router)
app.include_router(raggers_router)
app.include_router(settings_router)
app.include_router(admin_router)

try:
    app.mount("/static", StaticFiles(directory="app/web/static"), name="static")
except RuntimeError:
    # The vendor/ directory doesn't exist outside the built Docker image
    # (fetched by the Dockerfile) - harmless when running tests/tooling
    # directly against the source tree instead of the container.
    pass


@app.get("/healthz")
async def healthz() -> dict:
    return {"ok": True}


@app.get("/")
async def index(user: User | None = Depends(get_current_user)) -> RedirectResponse:
    return RedirectResponse(url="/events" if user is not None else "/login", status_code=303)
