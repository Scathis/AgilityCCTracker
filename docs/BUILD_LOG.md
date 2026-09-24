# Build Log

## Phase 0 — Server discovery (read-only) — done 2026-09-24

Findings:
- Ubuntu 24.04.4 LTS, Docker 29.5.2 / Compose v5.1.4. Root disk (`/dev/sda2`) 78%
  used / 26G free. Docker's actual data-root already lives on a second SSD
  (`/mnt/120ssd/containers/docker-data/`, migrated 2026-09-18 for an unrelated
  reason), so a plain named Docker volume for this project's MySQL lands there
  automatically, not on the tight root disk.
- 26 containers running, all healthy, including `gimhub-app` + `gimhub-mysql` +
  `gimhub-redis` (the pre-existing self-hosted GIM Hub — confirmed present and
  untouched; only read `docker ps`, nothing stopped/reconfigured).
- `127.0.0.1:8090` (the plan's preferred API port) is free.
- **No host-level reverse proxy** (no nginx/Apache/Caddy/Traefik installed) —
  this stack's actual mechanism is **Cloudflare Tunnel** (`cloudflared`, native
  systemd service, confirmed running). This is a deviation from
  `BUILD_PLAN.md`'s assumption of a `deploy/proxy/` nginx/Caddy snippet + reload
  — there is no local proxy config to write. Going public means: bind the API to
  `127.0.0.1:8090` (per plan), and the owner manually adds a Cloudflare Zero
  Trust Public Hostname pointing at `localhost:8090` — same as every other
  service on this host. Nothing in this repo can script that step.
- `ufw` is installed but status wasn't checked (needs a `sudo` password not
  available to this session) — not chased since the API will be bound to
  localhost-only, no inbound firewall port needed for it.
- git/curl/cron present; repo remote reachable.

Owner answers:
- **Deploy directory:** `/mnt/120ssd/agilitycctracker` (owner's second SSD, not
  the root disk — corrected from the plan's literal `/opt/agilitycctracker`
  default and from keeping it under `~/projects` on the root disk, since this
  host reserves the second SSD for anything with more than trivial disk
  footprint; a purpose-named top-level folder there, matching the existing
  `containers/` and `Mds/` convention on that drive).
- **Git branch:** continue on `claude/zealous-babbage-dw92it` (the branch this
  plan was delivered on). **Push to origin at the end of every phase** (owner
  confirmed, matches the existing GIM Hub fork workflow).
- **Public hostname:** `agilitycc.grestinator.com` (owner will add the
  Cloudflare Public Hostname manually once the API is up).
- **Dashboard admin username:** `grestinator`.
- **FC owner RSN:** **TBD** — owner confirmed the group is "Agility CC" but the
  channel owner wasn't online to check the key icon next to their name at time
  of asking. Only needed at Phase 7 (FC group creation); not a blocker for
  Phases 1–6.
- **FC display name:** `Agility CC`.
- **API port:** `8090`, confirmed free.

## Phase 1 — Scaffold — done 2026-09-24

Created:
- Plugin: `build.gradle`/`settings.gradle` (from `runelite/example-plugin`,
  `rootProject.name = 'agility-cc-tracker'`), Gradle 8.10 wrapper (generated via
  a throwaway `gradle:8.10-jdk11` container, `-u $(id -u):$(id -g)` so the
  output is host-owned, not root-owned), `runelite-plugin.properties`
  (`author=grestinator` — real name not yet given, easy to change later),
  `LICENSE` (BSD-2-Clause, copyright holder `AgilityCCTracker contributors` per
  the plan's own fallback since no real name was given), a generated placeholder
  `icon.png` (48x72, via local Pillow — no network image tool needed), minimal
  `AgilityCCTrackerConfig`/`AgilityCCTrackerPlugin` (just `apiUrl`/`apiToken` for
  now — full config per §5.1 comes in Phase 2), and the `AgilityCCTrackerPluginTest`
  launcher verbatim from §4.3.
- Server: `server/pyproject.toml` (hatchling backend, full dependency list from
  §6.1, ruff + pytest config), `server/app/main.py` (`GET /healthz` only),
  `server/Dockerfile`, `server/docker-compose.yml`, `server/docker-compose.test.yml`,
  `server/.env.example`, `server/tests/test_healthz.py`.
- `.gitignore` (Java/Gradle, Python, `.env`, `server/backups/`, IDE), and
  `.github/workflows/ci.yml` (job 1: Java 11 + `./gradlew build`; job 2: MySQL
  8.4 service + Python 3.12 + `ruff check` + `pytest`).

**Deliberate Dockerfile gap, not yet wired up:** `alembic.ini`/`alembic/` don't
exist yet (Phase 4 creates them), so the Dockerfile only `COPY`s `app/` for now
and runs plain `uvicorn`, not `alembic upgrade head && uvicorn`. Flagged with a
`TODO (Phase 4)` comment in the Dockerfile itself — pick this up when the first
migration exists, per `BUILD_PLAN.md` Phase 6 step 3.

**Two real bugs found and fixed while getting the server check green, not
guessed away:**
1. `pytest --compact` isn't a valid flag on the pytest version this pulls in
   (9.1.1) — errored with `unrecognized arguments: --compact` before a single
   test ran. Removed the flag from both `server/docker-compose.test.yml` and
   `.github/workflows/ci.yml` (plain `pytest`).
2. Even after that fix, pytest exited 5 ("no tests collected") — the
   `server/Dockerfile` only `COPY`s `app/` into the image, so `server/tests/`
   never made it in when `docker-compose.test.yml`'s `tests` service built from
   that same Dockerfile. Fixed by mounting `./tests:/app/tests` as a volume on
   the `tests` service in `docker-compose.test.yml`, rather than baking test
   files into the shared production image. Third attempt: ruff clean, `1
   passed`.

**Checks, both green:**
- `docker run ... gradle:8.10-jdk11 ./gradlew build --no-daemon` → `BUILD
  SUCCESSFUL`, 5/5 tasks.
- `docker compose -f docker-compose.test.yml run --rm tests` → `ruff check`:
  All checks passed; `pytest`: 1 passed.

No dangling Docker images left behind; root disk unaffected either way since
Docker's data-root already lives on `/mnt/120ssd` (see Phase 0 above).

Committed `Phase 1: scaffold plugin and server` and pushed to
`origin/claude/zealous-babbage-dw92it`.

## Phase 2 — Plugin core (§5.1–5.6)

Starting now.
