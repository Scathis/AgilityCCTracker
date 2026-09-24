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

## Phase 2 — Plugin core (§5.1–5.6) — done 2026-09-24

Created:
- **Full `AgilityCCTrackerConfig`** per §5.1 (Connection/Sharing/FC observer/
  Display sections, all 8 keys), replacing Phase 1's minimal 2-key version.
- **`state/DataState<T>`** exactly as given in §5.3, plus one addition beyond
  the literal spec: a `reset()` method (clears both pending and lastSent).
  The exact-spec code had no way to forget a stale `lastSent` across a
  relogin/world-hop, and `AgilityCCTrackerPlugin`'s required "reset all state
  on GameStateChanged→LOGIN_SCREEN/HOPPING and on account-hash change" (§5.2)
  needs exactly that.
- **`model/`**: `SelfState`, `EquipmentItem`, `LapEvent`, `DeathEvent`,
  `RosterMember`, `RosterSnapshot`, `IngestBatch`, `StatusResponse`,
  `ActiveEvent` — all Lombok `@Value` (immutable, proper `equals`, per §5.3's
  guidance), camelCase fields mapped to the server's snake_case JSON via a
  `FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES` Gson built with
  `gson.newBuilder()` inside `ApiClient` (never `new Gson()`, per the Hub
  rule) — no per-field `@SerializedName` needed. `loadoutChecks`/`dd` sections
  intentionally not modelled yet; added in Phase 3 once the scanner/DD
  tracker exist to populate them.
- **`collect/SelfStateCollector`**, **`LapTracker`**, **`DeathTracker`**,
  **`RosterTracker`** per §5.4, plus a small helper not explicitly named in
  the spec: **`collect/WildyLevelReader`** + **`util/WildernessLevel`** —
  factors the widget-text/coordinate-fallback wilderness-level detection into
  one shared, pure-testable place instead of duplicating it between
  SelfStateCollector (self.wildy_level) and DeathTracker (deaths[].wildy_level).
- **`api/ApiClient`** per §5.5 (token/URL validation, 401/403 latch until
  config changes, 429 60s backoff, network/5xx skip-next-10-attempts) and
  **`api/Uploader`** per §5.6 (2s upload tick building one `IngestBatch` from
  whatever's changed/queued, 60s `/status` poll into a new **`api/StatusHolder`**
  singleton shared with `RosterTracker`).

**Two real bugs found via the actual compiler, not guessed** (first `./gradlew
build` attempt, then verified each fix against real RuneLite/runelite source
via WebFetch before reapplying):
1. `ConfigChanged` doesn't live in `net.runelite.client.config` — confirmed
   via `raw.githubusercontent.com/runelite/runelite/master/.../client/events/ConfigChanged.java`
   that its real package is `net.runelite.client.events`.
2. `Skill.OVERALL` is now a **deprecated constant literally assigned `null`**
   (`@Deprecated public static final Skill OVERALL = null;`) and no longer
   appears in `Skill.values()` at all (confirmed: 24 real skills, Attack
   through Sailing, no Overall entry) — so §5.4's "skip OVERALL if present"
   check (`if (skill == Skill.OVERALL) continue;`) was comparing against
   `null` and was dead code from the moment it was written. Removed entirely
   rather than kept as a harmless-but-pointless check.

**Deliberate deviations from a literal spec reading, each with reasoning:**
- `RosterTracker`'s 5s debounce / 60s keep-alive resend is driven off the
  plugin's existing 1s `@Schedule` tick (`onTick()`) rather than injecting its
  own `ScheduledExecutorService` — whether RuneLite's Guice injector actually
  binds one at plugin scope couldn't be verified without a live client to
  test against (this server has no display — per §8/DEV_SETUP, that's the
  owner's PC's job), so avoided a design that could compile clean but fail at
  runtime with a Guice binding error. Fully equivalent behaviour, same debounce
  timing, and it's pure/testable with a fake clock as a bonus.
- Tile-based lap-completion detection (§5.4's secondary signal) deferred —
  it's explicitly a fallback "for lap timing if the chat message is disabled,"
  and nothing here disables that message.
- HP/prayer collection (mentioned in §5.4's 1s-tick list) deferred — neither
  the `self` JSON contract (§5.6) nor the `player_snapshots` schema (§6.2) has
  a field for it, so there's nothing downstream to consume it yet. Easy to add
  later if a real use turns up.

**Unit tests added, all pure-logic (no RuneLite `Client` mocking needed,**
**per §5.10's own preference):**
- `DataStateTest` — update/consume/restore/reset semantics, 7 cases.
- `WildernessLevelTest` — widget-text parsing + coordinate fallback, 4 cases.
- `LapTrackerParseTest` — regex against plain/coloured/comma-thousands/
  missing-trailing-period/unrelated messages, 5 cases (package-private static
  `parseLapCount` made testable by keeping the test in the same package).

**Checks, all green:** `./gradlew build --no-daemon` → `BUILD SUCCESSFUL`, all
unit tests passing, zero compiler warnings (confirmed by temporarily adding
`-Xlint:deprecation` to `build.gradle`, finding the one real warning above,
fixing it, then reverting `build.gradle` back to its exact §4.1 verbatim form
— `git diff build.gradle` shows no diff).

## Phase 3 — Plugin loadouts, scanner, DD, panel (§5.7–5.9)

Not started — see chat for the owner's go/pause decision before continuing,
since this phase adds `scan/FcMemberScanner`, the one class in this whole
project that reads *other* players.
