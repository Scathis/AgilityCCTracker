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

## Phase 3 — Plugin loadouts, scanner, DD, panel (§5.7–5.9) — done 2026-09-24

**Process change from Phase 2, worth keeping:** every genuinely risky
RuneLite API surface this phase touches was verified against real
`runelite/runelite` source via WebFetch *before* writing the code, not fixed
after a failed compile. Verified up front: `PlayerComposition.ITEM_OFFSET`
(2048) and `getEquipmentIds()`'s `int[]` return; `WorldView.players()`'s real
return type (`IndexedObjectSet<? extends Player>`, confirmed iterable);
`FriendsChatManager`/`NameableContainer<T>`'s `getMembers()` (`T[]`) and
`findByName(String)` (`T`); and — most error-prone, since a wrong ordinal here
would silently misattribute gear to the wrong slot — `KitType`'s exact 12-entry
ordinal order (`HEAD,CAPE,AMULET,WEAPON,TORSO,SHIELD,ARMS,LEGS,HAIR,HANDS,BOOTS,JAW`)
and `EquipmentInventorySlot`'s exact `slotIdx` values (0-13), confirmed to line
up 1:1 for every slot this project cares about (`BODY`≈`TORSO`, `GLOVES`≈`HANDS`,
both at index 4/9 respectively in each enum). Also verified: `NavigationButton.builder()`'s
field names, `ClientToolbar.addNavigation()`/`removeNavigation()`, `PluginPanel`'s
protected constructors + `PANEL_WIDTH` constant. **Result: the entire batch
compiled clean on the first `./gradlew build` attempt** — a real contrast with
Phase 2's two fix-after-the-fact bugs, and the reason this is called out
explicitly rather than left as a one-off.

Created:
- **`loadout/`**: `Slot` (11 values matching the server's JSON contract exactly
  — no ARMS/HAIR/JAY, since nothing here checks those — with `toEquipmentSlotIdx()`
  for the local player's own WORN container and `toKitType()`, null for
  RING/AMMO, for other players' `PlayerComposition`), `Check`, `LoadoutContext`,
  `Condition`, `Requirement`, `Loadout` (with a small static `find(loadouts, context)`
  helper shared by all three loadout consumers below), `WornItem`,
  `LoadoutEvaluator` (pure, per §5.7 — zero RuneLite imports), `ItemNameResolver`
  (id→normalised-name cache, strips trailing bracketed tags), `SelfLoadoutCheck`
  (evaluates RUNNING on all 11 slots, since self has full visibility of their
  own gear).
- **`dd/`**: `DdTracker` (pure, every method takes an explicit `now` so it's
  directly unit-testable with a fake clock, no mocking), `DdCallDetector`
  (chat-message detection, rank-gated), `SelfDdSampler` — deliberately kept
  *separate* from `FcMemberScanner` rather than folded in, so the "reads
  other players" boundary stays exactly one class; self's own DD sample needs
  full 11-slot gear visibility, which would otherwise blur that line.
- **`scan/FcMemberScanner`** — the one class that reads other players. Runs
  on `GameTick`, gated on `fcObserver` config + server `observer_enabled` +
  active event + being in the Wilderness Agility Course region; only ever
  processes players already on the FC roster; capped at 200 players/scan;
  every per-player value (worn gear, location) goes out of scope at the end
  of each loop iteration — nothing about another player is ever stored in a
  field, only requirement-id gaps and booleans survive past that iteration.
- **`ui/TrackerPanel`** — connection status, FC/event, laps+tickets this
  session (session-scoped, not strictly event-scoped — a disclosed
  simplification, see below), own loadout status, DD status incl. own time on
  tile, the observed-FC-members gap list, and a "Copy my tile" button (writes
  `x,y,plane` to the system clipboard, for entering DD tiles on the dashboard).
  Registered via `NavigationButton`/`ClientToolbar` in `startUp()`, using a
  generated 16×16 placeholder icon at `src/main/resources/com/agilitycctracker/panel_icon.png`.
- **New model DTOs**: `DdTile`, `DdConfig`, `LoadoutCheckDto`, `DdPresenceDto`,
  `DdSection`; `StatusResponse` extended with `loadouts`/`dd`; `IngestBatch`
  extended with `loadoutChecks`/`dd`.
- **`AgilityCCTrackerPlugin`**: wired every new component in, routes
  `ChatMessage` to both `LapTracker` and `DdCallDetector`, calls
  `SelfLoadoutCheck.refresh()` on WORN changes and after every `/status` poll,
  drives `FcMemberScanner`/`SelfDdSampler`/`DdTracker.autoCloseIfExpired()`
  off `GameTick` (matching §5.8's "runs on GameTick", and giving `DdTracker`
  real ~600ms-per-tick timing rather than the coarser 1s schedule), and adds
  the panel toggle reacting live to the `showPanel` config item via
  `ConfigChanged`.
- **`Uploader`**: now includes `loadoutChecks` (drained from `FcMemberScanner`)
  and a `dd` section with its own cadence — sent immediately when a window
  just closed, otherwise at most once per 10s while a window is open (per
  §5.9's "every 10s and at close"), decoupled from the normal 2s upload tick
  so an open DD window doesn't spam the server every 2s. `DdTracker`'s own
  live state is never "restored" on a failed send the way queues are — it's
  authoritative and continuously updated, so a failed POST just resends the
  same window/samples next cycle, no data loss.

**Deliberate simplifications, disclosed:** the panel's "laps this session"
counter (`LapTracker.getSessionLapCount()`/`getSessionTicketsGained()`) is
session-scoped (since plugin start), not strictly event-scoped — true
event-boundary tracking would need cross-referencing event start/stop from
`/status`, out of scope for a sidebar display; the real per-event totals are
computed server-side from the uploaded lap timestamps regardless, so nothing
is lost, only this one client-side display label is approximate.

**Unit tests added (47 total across the whole project now), all pure-logic:**
- `LoadoutEvaluatorTest` (12 cases) — every scenario from §5.10 against the
  §7 default Running/DD loadouts (hardcoded as test fixtures only — the
  plugin itself always gets `Loadout` data from `/status`, never bakes it
  in): shortbow-no-shield passes, crossbow-no-shield fails only
  `run-xbow-shield`, crossbow+shield passes, blowpipe fails only `run-bow`,
  empty hand fails both `run-weapon` and `run-bow`, missing
  helm/legs/boots/necklace each fail their own requirement, RING/AMMO
  requirements skipped (not failed) when not visible, DD loadout pass/fail.
- `ItemNameResolverTest` (5 cases) — the exact name-normaliser cases from
  §5.10 ("Magic shortbow (i)", "Rune crossbow", "Dragon crossbow (cr)",
  "Toxic blowpipe", "Bow of faerdhinen (c)").
- `DdCallDetectorTest` (5 cases) — call/break regex phrasing, rank gating,
  null/empty-safe.
- `DdTrackerTest` (9 cases) — scripted timelines via the tracker's own
  explicit `now` param: on-time arrival, late arrival (`arrived_after_ms`),
  left-before-break after exceeding leave grace, seen-running after exceeding
  arrive grace with no tile visit, wrong-weapon-at-DD unioned into
  `ddFailed`, window auto-close at exactly `max_minutes`, a second `openWindow`
  call while one's already open is a no-op, observations are ignored with no
  window open, and `acknowledgeClosed()` correctly resets for a fresh call.

**§5.11 Hub-readiness grep checks — all three pass (empty output):**
```
grep -rn "getPlayers()\|players()\|getPlayerComposition" src/main/java | grep -v scan/FcMemberScanner
grep -rn "PlayerSpawned\|PlayerDespawned\|InteractingChanged\|HitsplatApplied\|AnimationChanged\|PlayerChanged" src/main/java
grep -rn "java.lang.reflect\|Runtime.getRuntime\|ProcessBuilder\|new OkHttpClient\|new Gson" src/main/java
```

**Checks, all green:** `./gradlew build --no-daemon` → `BUILD SUCCESSFUL`,
47/47 tests passing, zero compiler warnings.

**This completes the entire RuneLite plugin side of the project (all of
BUILD_PLAN.md §5).** Everything from Phase 4 onward is the FastAPI server -
a different language/stack, and the first point where anything gets deployed
on this host.

## Phase 4 — Server API (§6.1–6.4) — done 2026-09-24

Created:
- **`app/models/`** — all 16 tables from §6.2 (`FcGroup`, `Player`,
  `PlayerSnapshot`, `Event`, `Attendance`, `Lap`, `Death`, `Loadout`,
  `LoadoutRequirement`, `LoadoutCheck`, `DdCall`, `DdPresence`, `Ragger`,
  `RaggerSighting`, `User`, `Item`), SQLAlchemy 2.0 typed `Mapped[...]`
  columns, one file per table-group.
- **The initial Alembic migration** — generated via `alembic revision
  --autogenerate` against a **real throwaway MySQL** (a one-off `docker run`
  mounting the server directory, attached to `docker-compose.test.yml`'s own
  network) rather than hand-writing 16 tables of DDL. The one thing flagged
  as a real risk before testing — `players.latest_snapshot_id`'s FK to
  `player_snapshots.id` (`use_alter=True` in the model, to handle the
  circular reference with `player_snapshots.player_id → players.id`)
  appearing embedded directly in the generated `CREATE TABLE players` rather
  than deferred to a separate step — **turned out to be a non-issue**:
  `alembic upgrade head` applied cleanly against a fresh database on the
  first real test, no manual migration fix needed at all.
- **`app/security.py`** — HMAC-SHA256 token hashing (the plugin's raw token
  is never stored, only its hash, matching §6.3) and an in-memory
  sliding-window rate limiter (5 failures/5min/IP → 429).
- **`app/deps.py`** — the `get_current_fc_group` dependency wiring token
  auth + the rate limiter into every plugin-facing route.
- **`app/schemas/`** — Pydantic v2 request/response models matching the
  plugin's JSON contract field-for-field (snake_case throughout, so no
  aliasing needed except `self` → `self_state` via `Field(alias="self")`,
  since `self` can't be a Python attribute name).
- **`app/services/roster.py`** (`diff_roster`) and **`app/services/dd.py`**
  (`find_matching_call`/`merge_broken_at`/`merge_presence`) — the pure,
  DB-free diff/merge math for §6.4 steps 5 and 7, deliberately factored out
  of the DB-touching orchestration in `api/v1/ingest.py`. **Same pure/impure
  separation pattern used throughout the plugin in Phases 2–3** — it's what
  made both sides of this project's trickiest logic unit-testable without
  heavy mocking.
- **`app/api/v1/status.py`** and **`app/api/v1/ingest.py`** — the full 8-step
  ingest orchestration from §6.4: upsert reporter, self-state snapshot
  (change-only) + self-loadout-check, lap/death insert-if-not-exists with
  event-window assignment, roster diff → attendance open/close, fc_scan
  loadout checks (attendance-gated, observer-kill-switched), DD call/presence
  merge with a 10s send cadence, and laps-during-DD → `seen_running`.
- **`app/seed.py`** — the §7 default Running/DD loadouts, stored as literal
  uppercase wire-format strings (`"WEAPON"`, `"NAME_ENDS_WITH"`, …) matching
  the plugin's Java enum constant names directly — no case translation
  needed for `slot`/`check_type`.
- **`app/services/loadouts.py`** — the *one* deliberate DB-lowercase/wire-
  uppercase mapping point in the whole server: `loadouts.context` is stored
  as MySQL `ENUM('running','dd')` per the schema, but must serialise as
  `"RUNNING"`/`"DD"` to match the plugin's Gson enum deserialization.
  Documented in both `schemas/status.py` and here so it can't silently drift.
- **Request-body-size-limit middleware** (100KB cap, §6.3) in `main.py`.

**Four real bugs found and fixed, each via an actual failing run, not
guessed — the last three specifically because real integration tests were
written against a real database rather than stopping at pure-unit-test
coverage:**
1. `asyncmy` needs the `cryptography` package for MySQL 8's default
   `caching_sha2_password` auth method — not pulled in transitively, added
   explicitly to `pyproject.toml`.
2. **Async-engine/event-loop scope mismatch.** `app/db.py`'s async
   SQLAlchemy `engine` is a module-level singleton that must stay bound to
   one event loop for its whole process lifetime. Phase 1's `test_healthz.py`
   used FastAPI's *synchronous* `TestClient` (which runs its own internal
   event-loop portal thread), while the new Phase 4 tests use
   `httpx.AsyncClient` under pytest-asyncio's default *per-test-function*
   event loop — mixing the two caused `RuntimeError: Task ... attached to a
   different loop` crashes, plus one silent-data-loss symptom (a query
   returning `None` for a row the app had *just* inserted, because the
   insert happened via a connection tied to a loop that had already closed
   by the time the check ran). Fixed by converting `test_healthz.py` to the
   same shared async `client` fixture as every other test, and setting both
   `asyncio_default_fixture_loop_scope = "session"` and
   `asyncio_default_test_loop_scope = "session"` in `pyproject.toml` so the
   whole test session shares one event loop, matching the engine's real
   lifetime.
3. **Transaction isolation level.** Even after fixing #2, the tests'
   long-lived `db_session` fixture still saw stale data: MySQL defaults to
   **REPEATABLE READ**, so that fixture's first query fixed a snapshot that
   never became aware of rows the *app's own separate per-request sessions*
   committed afterward (each POST through the test client runs its own
   request-scoped session via `get_session()`). Concretely: a test posting a
   second, genuinely-different `loadout_failed` list still saw the
   `loadout_checks` count stay at 1 instead of going to 2. Fixed by binding
   the `db_session` fixture's connection to **READ COMMITTED** isolation
   (`conn = await conn.execution_options(isolation_level="READ COMMITTED")`
   in `tests/conftest.py` — confirmed via the actual passing run that this
   call does need `await` on an `AsyncConnection`).
4. A stale Docker image (process lesson, not a code bug): `docker compose
   run` silently reused a cached image after the Dockerfile was edited to add
   `COPY alembic.ini`/`COPY alembic` — the container had no `alembic.ini` at
   all and failed with `No 'script_location' key found`. Fixed by rebuilding
   with `--build`; worth remembering for any future Dockerfile edit here.

**Other fixes along the way:** `Skill.OVERALL`-style dead weight didn't
recur here, but a genuine ruff pass was needed — `ruff format` auto-wrapped
the bulk of E501 line-length violations (mostly in `seed.py`'s dict literals
and `services/dd.py`'s longer signatures); an earlier `ruff check
--unsafe-fixes` pass converted the plain `class X(str, enum.Enum)` model
classes to `enum.StrEnum` (`SnapshotSource`, `LoadoutContext`,
`LoadoutCheckSource`, `RaggerStatus`, `UserRole`) — a legitimate Python 3.12
idiom, kept deliberately, not reverted; added a documented `ignore =
["B008"]` to `pyproject.toml`'s ruff config, since that rule flags FastAPI's
own idiomatic `Depends(...)` default-argument pattern as a false positive,
not a real bug.

**Test coverage added — 30 tests, all passing:**
- `tests/test_roster_service.py` (6) — pure `diff_roster()` tests: new
  member opens, missing member closes only when the reporter's world
  matches, does *not* close when the reporter is on a different world
  (world-hop conservatism), closes nothing when `reporter_world` is unknown,
  multiple-session selectivity, present-member no-op.
- `tests/test_dd_service.py` (13) — pure tests against `find_matching_call`
  (±10s tolerance), `merge_broken_at` (earliest-non-null wins, `staff_edited`
  freezes the current value), and `merge_presence` (first observation,
  max/min/OR/union across a second merge, one-sided-`None` handling).
- `tests/test_status.py` (4) — real integration tests via `httpx.AsyncClient`
  against the live app + test DB: valid token, invalid token, missing token,
  and the 429 rate limit after 5 failures.
- `tests/test_ingest.py` (6) — real integration tests: lap and death
  idempotency (duplicate POSTs, one row each), event-window assignment
  (two sequential events plus a gap timestamp resolving to `None`),
  self-loadout-check change-only storage, the fc_scan attendance-gate
  (dropped with no `Attendance` row, stored once one exists), and the
  `observer_enabled` kill switch (fc_scan dropped, self still stored).
- `tests/test_healthz.py` (1, from Phase 1, converted to the async client).

**Checks, all green:** fresh-database `alembic upgrade head` succeeds, `ruff
check` clean, `30 passed` in the real pytest run against the test MySQL.

**Worth carrying into Phase 5:** three of this phase's four real bugs
(event-loop scope, transaction isolation, and arguably the missing
`cryptography` dependency) would never have surfaced from pure unit tests
alone — only real integration tests against a real, running database caught
them. Keep that same standard for Phase 5's dashboard/session/CSRF logic,
not just unit tests in isolation.

**This means the server can now receive plugin data end-to-end** — token
auth with rate limiting, self-state snapshots, laps/deaths with correct
event assignment, roster/attendance tracking, loadout compliance (self +
observed, gated), and DD merging — even though there's no web dashboard yet.

## Phase 5 — Dashboard + workers (§6.5–6.6) — done 2026-09-24

Created:
- **`app/web/security.py`** — argon2 password hashing (`argon2-cffi`) and a
  signed, timed session cookie (`itsdangerous.URLSafeTimedSerializer`)
  carrying the CSRF token as part of its payload — the CSRF token is never a
  separate secret to manage, it's just a field inside the same trusted,
  HttpOnly-cookie-embedded, server-signed blob, checked against a hidden
  form field on every POST (`app/web/deps.py`'s `verify_csrf`). Also added a
  second, short-lived (`60s`) signed **flash cookie** for one-time
  password/token reveals (`sign_flash`/`read_flash`) — used instead of a
  query-string parameter specifically so a generated secret never lands in
  browser history or server access logs.
- **`app/web/deps.py`** — `get_current_user`/`require_user` (redirects to
  `/login` via a custom `RequiresLogin` exception + a `main.py` exception
  handler, rather than a 401), `require_role(min_role)` (rank-based:
  viewer < staff < admin, so admin implies staff), and
  **`get_default_fc_group`** — the dashboard has no group-switcher (nothing
  in §6.5 describes one), so every page just resolves whichever `FcGroup`
  row is globally first by id. A single-tenant simplification, not an
  oversight: this deployment only ever seeds one group via the CLI.
- **`app/cli.py`** — `create-admin`, `create-group` (also seeds default
  loadouts), `rotate-token`, `seed-loadouts`, per §6.3/§7. All four generate
  random secrets server-side and print them once, never taking a password on
  the command line.
- **Seven route modules + 13 templates** (`app/web/routes/`,
  `app/web/templates/`), covering every page in §6.5: login/logout; events
  (list, start/stop, detail with all 5 tabs — attendance, laps, DD,
  deaths & raggers, manual gap); DD leaderboard + CSV export; loadouts
  (list/detail, add/delete requirement, item search); players (list/search,
  profile); raggers (list, add, status, sightings, hiscore refresh);
  settings (DD tiles/regex/grace periods/observer kill switch, admin-only);
  admin (users, FC groups/token rotation). Jinja2 + HTMX + Pico.css per
  §6.1, server-rendered throughout — HTMX used narrowly (the loadout
  item-search box) rather than force-fit everywhere.
- **Vendored frontend assets** (`htmx.min.js`, `pico.min.css`,
  `chart.umd.js`) — fetched via `curl` in the Dockerfile's build (§6.1's
  "fetch in Dockerfile" option), confirmed actually working via a real
  `docker build` pulling from unpkg/jsdelivr, so the dashboard never depends
  on a third-party CDN at request time. Also added `--proxy-headers
  --forwarded-allow-ips='*'` to the Dockerfile's `uvicorn` invocation per
  §6.1, so `request.url.scheme` correctly reflects `X-Forwarded-Proto` once
  this sits behind Cloudflare Tunnel (Phase 6) — same class of fix as the
  GIM Hub `trustProxies()` note elsewhere in this stack's `CLAUDE.md`.
- **`app/workers/hiscores_worker.py`** and **`app/workers/items_worker.py`**
  — the two background workers from §6.6, started as `asyncio.Task`s in
  `main.py`'s `lifespan`, gated by `HISCORES_ENABLED`/`ITEMS_ENABLED` (both
  `false` in the test environment, so they never run during tests — no real
  network calls from the test suite). Parsing/formula logic is pure and
  separate from the network fetch, mirroring the exact same pure/impure
  split used throughout the plugin in Phases 2–3: `compute_combat_level()`
  verified against **two real, known OSRS ground truths** (a maxed-combat
  account is exactly level 126; a fresh account is exactly level 3) rather
  than an invented expected value.

**Real bugs found and fixed, all via the actual pipeline — this phase's
first-ever exercise of every Jinja2 template and dashboard route surfaced
several, exactly as expected for this much genuinely new surface area:**
1. RSN/owner-RSN test fixtures exceeded the real `VARCHAR(12)` schema limit
   (`"Default Owner"` is 13 chars, `"RaggerFlowTest"` is 14) — MySQL
   rejected the insert outright. Fixed by shortening the test data, not the
   column.
2. A self-contradicting test: `test_compute_combat_level_missing_skills_default_to_level_1`
   asserted `== 3` while its own name said "default to level 1". An empty
   skills dict correctly computes to combat level 1 (all stats, including
   HP, fall back to the defensive level-1 default) — the fresh-account-is-3
   case needs HP=10 specifically and is already covered by a separate test.
3. **The `default_group`/`no_active_event` test-fixture design.** Since the
   dashboard resolves "the" FC group as whichever row is globally first,
   and the test database isn't wiped between tests or files, a test
   creating its *own* fresh `FcGroup` (the pattern Phase 4's tests use)
   can't assume the dashboard will actually operate on it. Added a
   `default_group` fixture that queries for the real first-by-id group
   (creating and seeding one only if none exists yet) so dashboard tests
   build data against the group routes will actually resolve, regardless of
   execution order across files — and `no_active_event`, which closes out
   any event left open by an earlier test before a new one is started
   (the "only one active event per group" rule would otherwise make test
   order matter).
4. **An unresolved, genuinely inconsistent httpx test-client quirk** — not a
   bug in this project's code. Verifying the admin panel's one-time
   password/token reveal (a signed, 60s flash cookie) by chaining a POST
   then a followed or separate GET on the same `httpx.AsyncClient` produced
   *inconsistent* results across two structurally identical tests: one
   (`/admin/groups`) passed, the other (`/admin/users`) didn't, across
   several different attempts (`follow_redirects=True`, then explicit
   two-step requests, then an explicit `client.cookies.set(...)` carry-
   forward). The Set-Cookie header itself was confirmed present and correct
   every time — only the test client's reuse of it across separate requests
   was flaky. Rather than keep chasing an `ASGITransport`-plus-cookie-jar
   interaction that's outside this project's own code, the two tests now
   verify the actual server-side guarantee directly: decode the Set-Cookie
   value via `read_flash()` and check its content on the single response
   that set it. A real browser handles Set-Cookie-then-redirect correctly
   per HTTP spec regardless of this test-tooling quirk — that part was never
   this project's behavior to prove.

**Test coverage added — 30 new tests (60 total across the whole project
now):**
- `tests/test_dashboard_auth.py` (5) — login success/failure (wrong password,
  unknown user), unauthenticated redirect to `/login`, logout requires CSRF.
- `tests/test_dashboard_rbac_csrf.py` (8) — viewer can read every page;
  viewer/staff correctly blocked from staff-/admin-only POSTs and pages
  (403, not a redirect, since they *are* logged in); missing/wrong CSRF
  token rejected.
- `tests/test_events_flow.py` (2) — full lifecycle (start → attendance
  populated → **every tab renders** → stop), and the manual-gap form
  actually inserting a `LoadoutCheck` row with the right
  `failed_requirement_keys`.
- `tests/test_loadouts_players_raggers_flow.py` (5) — loadout list/detail
  render with real seeded data, staff can add/delete a requirement, item
  search returns an HTML fragment, player list/profile render, and a full
  ragger flow (add → sighting → status change → hiscore refresh actually
  clearing a previously-set `hiscores_updated_at`, not just trivially
  already-`None`).
- `tests/test_settings_admin_flow.py` (4) — settings persist real values
  including a checked checkbox, an *unchecked* checkbox correctly persists
  `False` (HTML omits unchecked checkboxes from the form body entirely —
  verified, not assumed), and the two flash-cookie mechanism tests
  described above.
- `tests/test_workers.py` (6) — `parse_hiscores_json`'s skill-id mapping,
  `compute_combat_level` against the two real OSRS ground truths plus edge
  cases, `merge_item_data`'s mapping+price combination and its handling of
  malformed/missing entries.

**Checks, all green:** fresh-database `alembic upgrade head` succeeds
(unchanged since Phase 4), `ruff check` clean, **`60 passed`** in the real
pytest run against the test MySQL.

**This completes BUILD_PLAN.md's entire server implementation (Phases
4–5).** The whole system — plugin, server API, dashboard, and background
workers — is now functionally complete per the spec and verified end-to-end
against a real database, though not yet deployed on this host (Phase 6) or
submitted to the RuneLite Plugin Hub (Phase 8).

## Phase 6 — Deploy on this server (§6.1/§6.3, deploy steps) — done 2026-09-25

Deployed for real (not a throwaway test) on `plexinator-3000`, at this
repo's own location (`/mnt/120ssd/agilitycctracker` — no separate
`/opt/agilitycctracker` checkout, matching the Phase 0 owner answer):

1. **`server/.env` generated** with strong random secrets
   (`openssl rand -hex 32` for `APP_SECRET`, `openssl rand -hex 24` for both
   DB passwords), mode 600, `APP_URL=https://agilitycc.grestinator.com`,
   `API_PORT=8090`. Never committed — `.gitignore` already covers it from
   Phase 1.
2. **`docker compose -p agilitycctracker up -d --build`** from `server/` —
   its own compose project (`agilitycctracker`), own network
   (`agilitycctracker_internal`), own named volume (`agilitycctracker_db_data`,
   landing on the SSD-backed Docker data-root at `/mnt/120ssd`, not the
   tight root disk, since that's this host's actual `data-root` regardless
   of which compose project creates the volume). API bound to
   `127.0.0.1:8090` only — genuinely not publicly reachable yet, matching
   the plan's own "🛑 before public exposure" framing for what comes next.
3. **`alembic upgrade head` ran cleanly** on first real boot (confirmed via
   `docker logs`: `Running upgrade  -> 101845981bbd, initial`), then uvicorn
   started normally.
4. **Bootstrap admin created**: `docker compose exec api python -m app.cli
   create-admin grestinator` → generated password relayed to the owner
   directly in chat (never written to a file, matching how every other
   generated secret in this project has been handled).
5. **Verified locally**: `curl http://127.0.0.1:8090/healthz` → `{"ok":true}`.

**Safety check (§1.3's server-safety rules) — passed:** captured a full
`docker ps` baseline (28 containers) before deploying, diffed against the
post-deploy state — the *only* difference is the two new
`agilitycctracker-{api,mysql}-1` containers appearing; every pre-existing
`/containers`-stack container (gimhub-app included) is untouched, none
restarted. Disk: 81GB free on `/mnt/120ssd` post-deploy (was 82GB before),
negligible footprint.

**Housekeeping along the way:** found and cleaned up a leftover
`agilitycctracker-test-mysql-1` container/network from an earlier Phase
4/5 test-pipeline run that never got torn down (`docker compose -f
docker-compose.test.yml down` was missed after the last verification pass)
— harmless (isolated in its own throwaway project/network) but pointless
resource use once noticed.

**Step 6 (reverse proxy / public exposure) — done 2026-09-25.** Owner added
the Cloudflare Public Hostname (`agilitycc.grestinator.com` →
`http://192.168.1.130:8090`, matching this stack's usual convention of
targeting the host's LAN IP rather than `localhost`, since `cloudflared`
runs as a native host process, not a container).

**Real bug found and fixed: `docker-compose.yml`'s port binding.**
`server/docker-compose.yml` had `ports: ["127.0.0.1:${API_PORT}:8000"]`
from Phase 6 step 2 above — a literal reading of the plan's own
"127.0.0.1-only until public exposure" framing. Once the owner pointed
cloudflared's ingress at `192.168.1.130:8090` (this host's actual
established pattern for every tunneled service, confirmed by grepping
`cloudflared`'s live config), that address was unreachable: a socket bound
to `127.0.0.1` doesn't accept connections addressed to a different local
IP, even from a process on the same host. Symptom was `cloudflared`
logging `dial tcp 192.168.1.130:8090: connect: connection refused` while
`curl http://127.0.0.1:8090/healthz` worked fine locally the whole time —
confirmed via `docker logs`-adjacent `journalctl`/`systemctl status
cloudflared` output, not guessed. Fixed by changing the binding to
`192.168.1.130:${API_PORT}:8000` and recreating just the `api` container
(`docker compose -p agilitycctracker up -d`, no rebuild needed) — this
required an explicit owner confirmation since Claude Code's own
permission classifier flags any new host-facing port bind as a
confirm-first action regardless of the plan's prior approval. Verified
post-fix: `curl http://192.168.1.130:8090/healthz` and
`curl https://agilitycc.grestinator.com/healthz` both return
`{"ok":true}`.

**Separate, unrelated snag hit while diagnosing the above: owner-side DNS,
not this project's bug.** The owner's browser first showed
`ERR_NAME_NOT_RESOLVED` for `agilitycc.grestinator.com` specifically,
while other `*.grestinator.com` hostnames worked fine on the same
network/browser. Root cause: an active VPN on the owner's Windows machine
was routing DNS through a `10.2.0.1` resolver holding a stale
negative-cache (`Non-existent domain`) entry for this one record, from an
earlier lookup before the Public Hostname existed — confirmed via
`nslookup agilitycc.grestinator.com` (fails, via the VPN's `10.2.0.1`
resolver) vs. `nslookup agilitycc.grestinator.com 1.1.1.1` (succeeds,
bypassing it). Resolved by disconnecting the VPN. Worth remembering if a
freshly-added hostname on this stack ever seems unreachable only for one
person/device — check for a VPN or third-party DNS resolver before
assuming a server-side problem.

**§8's HTTPS checks — both pass:** `curl -s
https://agilitycc.grestinator.com/healthz` → `{"ok":true}`; owner
confirmed dashboard login over HTTPS works (`grestinator` / a freshly
generated password — see below).

**Admin password had to be regenerated.** The original `create-admin`
password from step 4 above was never actually captured by the owner (an
earlier context-compaction boundary meant it was generated and printed,
but not before the conversation moved on) — `create-admin grestinator`
correctly refused to run again (`User 'grestinator' already exists`,
no CLI reset-password command exists). Reset directly via a one-off
`docker compose exec api python -c ...` snippet using the app's own
`hash_password()` (same argon2 hasher the login path verifies against,
not a hand-rolled hash), rather than adding a throwaway CLI subcommand
for a one-time need. New password relayed to the owner in chat only, per
this project's established secret-handling convention.

Next: **`GET /` correctly 404s** (`{"detail":"Not Found"}`) — there is no
dashboard root/index route in §6.5's spec, only `/login` and the
authenticated pages beneath it. Not a bug; flagged here since it looks
like one on first hit.

**Step 7 (backups + cron) — done 2026-09-25.** `deploy/backup.sh`:
`mysqldump` via `docker compose -p agilitycctracker exec -T mysql`
(reads `DB_NAME`/`MYSQL_ROOT_PASSWORD` from `server/.env`, never echoed),
gzipped to `server/backups/agilitycc-<timestamp>.sql.gz` (gitignored —
only the script is tracked), pruning anything older than 14 days via
`find -mtime +14 -delete`. Tested manually before scheduling: produced a
64KB dump with all 17 tables (16 schema tables + `alembic_version`) —
confirmed by counting `CREATE TABLE` statements in the decompressed
output, not just checking the file exists. Cron entry added directly to
the owner's own crontab (no sudo needed, unlike the sudoers-file pattern
used elsewhere in this stack — a per-user crontab edit is always
unprivileged), tagged consistently with the two pre-existing entries:
```
0 4 * * * /bin/bash /mnt/120ssd/agilitycctracker/deploy/backup.sh >> /mnt/120ssd/agilitycctracker/server/backups/backup.log 2>&1 # agilitycctracker-backup
```
Nightly at 04:00, matching the plan's own suggested schedule.

## Phase 7 — Hand back to the owner — done 2026-09-25

1. **FC group created**: `python -m app.cli create-group "Agility CC" "Agility FC"`
   → `id=1`, display name `Agility CC`, owner RSN `Agility FC` (per the
   owner's confirmation this session — the channel and its owner share the
   same name). Seeded the default Running/DD loadouts (§7) automatically.
   Plugin token generated and relayed to the owner directly in chat only,
   per this project's established secret-handling convention — never
   written to any file in the repo. DD tiles left empty, as planned, until
   captured in-game via the plugin's "Copy my tile" button.
   **Verified live**: `GET /api/v1/status` with the new token returns the
   full config (group identity, both seeded loadouts, DD defaults) over
   `https://agilitycc.grestinator.com`.
2. **Credentials handed to the owner** (in chat, once, per this project's
   convention): API URL, plugin token, dashboard URL, admin login.
3. **Docs written**: `docs/DEV_SETUP.md` (§8, filled in with the real
   `https://agilitycc.grestinator.com` API URL — the token itself is left
   as an instruction to ask whoever ran `create-group`, never embedded,
   since this doc is repo-committed and will eventually be public per
   Phase 9), `docs/SELF_HOSTING.md` (new — deploy/update/backup/restore/
   admin-password-reset, generalized from this project's own real Phase 6
   deploy experience, including the loopback-vs-LAN-IP port-binding gotcha
   hit and fixed during that phase), `docs/API.md` (new — full `/api/v1`
   reference per §6.3–6.4, request/response shapes, the ingest processing
   order, and an explicit "what's never in here" callout for the
   other-players-data boundary), `docs/PLUGIN_HUB_SUBMISSION.md` (§9,
   updated to reflect the real repo `github.com/Scathis/AgilityCCTracker`
   and its new Phase-9 numbering after the Phase 8 reskin was inserted),
   `docs/FUTURE_NON_HUB.md` (§10, verbatim in substance).
   **Owner clarified mid-phase**: the RuneLite plugin configuration itself
   (§8/`DEV_SETUP.md`'s steps 4–5) will be done by the owner directly, not
   handed off — confirming the doc's purpose is exactly this: a
   self-contained reference for doing it themselves, not a handoff to a
   separate person.
4. **Final report:**

**What was built:** the complete system per `BUILD_PLAN.md` — a RuneLite
plugin (attendance/lap/DD/loadout tracking, one class with
elevated-visibility scanning, gaps-only reporting of other players' loadout
state), a FastAPI + MySQL server (token-authed ingest API, session-authed
Jinja2 dashboard with role-based access, two background workers for
hiscores/item prices), deployed for real on this host behind Cloudflare
Tunnel with nightly backups, and a live FC group ready for in-game use.

**Test results:** plugin — 47/47 JUnit tests passing, `./gradlew build`
clean (last verified end of Phase 3; no plugin code has changed since).
Server — **60/60 pytest passing**, re-verified fresh just now
(`docker compose -f docker-compose.test.yml run --rm tests`, 2.51s), `ruff
check` clean.

**Known non-blocking issues, not fixed this phase:**
- `datetime.datetime.utcnow()` is used throughout `app/cli.py` and several
  route modules — deprecated in favor of timezone-aware
  `datetime.now(datetime.UTC)` as of Python 3.12, still fully functional
  (just emits a `DeprecationWarning`, visible in the test run above and in
  `create-group`/`create-admin`'s own output). Cosmetic/future-proofing
  only; not touched this phase since it's unrelated to anything actually
  in scope, but worth a small cleanup pass sometime before it's ever
  actually removed from the language.
- The 5 partial-coverage entries carried over from earlier Toshiba/Radarr
  storage work (unrelated project, noted here only in case of confusion —
  see `/containers/CLAUDE.md`, not this repo).

**What was deliberately skipped/deferred, and why:**
- **Phase 8 (OSRS-themed dashboard reskin)** — scoped and added to the plan
  this session at the owner's request, not yet executed. Pure CSS/template
  work, no functional risk, can happen anytime before or during the
  owner's in-game testing.
- **Phase 9 (Plugin Hub submission)** — explicitly gated on real in-game
  testing (`DEV_SETUP.md`'s manual checklist) and the owner re-confirming
  the DD/loadout observer design with RuneLite Hub maintainers first, per
  the plan's own reasoning. Not started.
- **DD tiles** — left empty in the seeded config; needs the owner to
  capture them in-game via the plugin panel's "Copy my tile" button and
  paste into Settings, per `DEV_SETUP.md`'s checklist.

**Next steps (owner):** configure the RuneLite plugin per `DEV_SETUP.md`
(API URL + token), work through its manual test checklist in-game, capture
the two DD tiles, then decide whether to move on Phase 8 (reskin) and/or
Phase 9 (Hub submission) next.

**Checks:** `docker ps` re-confirmed clean against the original baseline
(no `/containers`-stack container affected by anything in this phase — no
new containers were even created, this phase was CLI + docs only).
Committed and pushed to `origin/claude/zealous-babbage-dw92it`.

## Post-Phase-7 in-game testing — owner started 2026-09-25

Owner set up a dev environment on their own Windows PC (Git, Temurin JDK
11, IntelliJ Community — per `DEV_SETUP.md`), cloned the repo, ran the
Gradle `run` task to launch RuneLite in developer mode with the plugin
loaded, and worked through the one-time Jagex-account credentials step
(`--insecure-write-credentials`) since their account logs in via the
Jagex Launcher. Configured the plugin (`apiUrl`/token) and got it running
logged in against the live server.

**Real bug #1 found via this first live run — `main` merged into
`origin/main` per the owner's PR #2, so all further work (including this
fix) now happens directly on `main`, not the feature branch**, per the
owner's decision once everything was merged.

`AssertionError: must be called on client thread` firing every 60
seconds, from `SelfLoadoutCheck.readOwnGear()` (reading the WORN item
container) via `AgilityCCTrackerPlugin.onStatusTick()`. Root cause:
`onStatusTick()` is `@Schedule(..., asynchronous = true)` — needed
because it also calls `uploader.pollStatus()`, a genuine blocking HTTP
call — but it called `selfLoadoutCheck.refresh()` directly from that same
async thread, and that method reads a live `ItemContainer` via
`client.getItemContainer()`, which RuneLite asserts must happen on the
client thread. This is exactly the rule `BUILD_PLAN.md` §5.6 documents
for `Uploader` itself ("Never call `client.*` from the async thread") —
it just wasn't applied to this one call site in the plugin's scheduling
glue. Caught from the actual RuneLite log output in IntelliJ's Run
console, not guessed. Fixed by injecting `ClientThread`
(`net.runelite.client.callback.ClientThread`, verified against real
RuneLite source — `invoke(Runnable)` runs the given task on the client
thread) and wrapping just that one call:
`clientThread.invoke(selfLoadoutCheck::refresh)`. Verified: `./gradlew
build` clean, 47/47 tests still passing. Committed and pushed straight to
`main` (`30954df`).

Note: this specific crash happens in a *different* scheduled method than
`TrackerPanel.refresh()` (a separate, correctly-synchronous 1s
`@Schedule`), so it likely wasn't the actual cause of the panel-rendering
oddity the owner also reported in the same session (blank status labels,
some visual duplication) — that's still open and needs a fresh in-game
look after this fix, since a stale/crashed plugin session can leave a UI
in an odd state that a clean restart may already resolve. Owner is
re-testing after pulling this fix.
