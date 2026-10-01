from pathlib import Path

from fastapi.templating import Jinja2Templates

TEMPLATES_DIR = Path(__file__).parent / "templates"
templates = Jinja2Templates(directory=str(TEMPLATES_DIR))

# Cache-busting suffix for vendored/static asset links (?v=<this>) - bump
# whenever a static CSS/JS/font file changes. Needed because Cloudflare's
# edge cache (sitting in front of this app via the tunnel) caches static
# file types by default; without a version bump, a deploy can silently
# keep serving a stale cached copy of an unchanged URL - confirmed
# happening to the OSRS theme CSS on 2026-10-01 (the server had the fix,
# Cloudflare was still serving the previous version). A new URL per bump
# sidesteps needing a manual cache purge entirely.
ASSET_VERSION = 5
templates.env.globals["asset_version"] = ASSET_VERSION
