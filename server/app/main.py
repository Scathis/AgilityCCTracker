from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware

from app.api.v1.ingest import router as ingest_router
from app.api.v1.status import router as status_router

MAX_BODY_BYTES = 100 * 1024


class BodySizeLimitMiddleware(BaseHTTPMiddleware):
    """Rejects oversized plugin request bodies with 413, per BUILD_PLAN.md §6.3."""

    async def dispatch(self, request: Request, call_next):
        content_length = request.headers.get("content-length")
        if content_length is not None and int(content_length) > MAX_BODY_BYTES:
            return JSONResponse(status_code=413, content={"detail": "Request body too large"})
        return await call_next(request)


app = FastAPI(title="Wildy Agility FC Tracker")
app.add_middleware(BodySizeLimitMiddleware)

app.include_router(status_router, prefix="/api/v1")
app.include_router(ingest_router, prefix="/api/v1")


@app.get("/healthz")
async def healthz() -> dict:
    return {"ok": True}
