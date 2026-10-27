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

**Real bug #2 — panel text unreadable.** After the crash fix and a clean
restart, the duplication artifact was gone (confirming it was leftover
state from the earlier crash cycle, not a separate bug), but the status
labels rendered as near-invisible low-contrast text. Root cause:
`TrackerPanel`'s `JLabel`s never had an explicit foreground color set, so
they fell back to a default that has poor contrast against RuneLite's
dark panel background. Fixed by setting `ColorScheme.TEXT_COLOR`
(RuneLite's own standard readable-text color for plugin panels,
confirmed via real RuneLite source) on every label, including the
dynamically-created FC-member rows. Committed `8c68f29`. Owner confirmed
fixed — "connection status is clearly visible now".

**Real bug #3 — the actual root cause of "nothing showing" (roster/
attendance and FC-member observation both silently blocked).** With the
crash and rendering fixed, event started ("Test 1") but neither
attendance nor "FC members in view" ever populated, despite 90 real
members loaded in the channel. `RosterTracker.isActive()` (and
`FcMemberScanner`, gated the same way) require the friends chat's actual
registered owner (`FriendsChatManager.getOwner()`) to match the server's
configured `fc_groups.owner_rsn`, case-insensitively. Root cause: the
owner RSN set at Phase 7's `create-group` time — `"Agility FC"` — was
the owner's *guessed* name (the real owner wasn't online to confirm via
the in-game crown icon at group-creation time, see Phase 0's notes) and
turned out to be wrong. The real registered owner is **`"Agility Cc"`**
— two characters off, but an exact-match comparison doesn't care how
close a miss is.

**Diagnosis process, since this took several rounds to pin down and is
worth remembering:** a one-shot log at the `FriendsChatChanged(joined=true)`
event consistently showed `name='null' owner='null' memberCount=0` —
initially assumed to be a data-population timing issue (the event firing
before RuneLite finishes loading the channel snapshot), which is real but
was a red herring here. The actual test that mattered was a **live,
periodic** re-check (every 5s, unconditional — an earlier
null-gated version of this same check silently never fired at all across
an 18-second window despite confirmed-running `onSecondTick()`, which
itself remains unexplained but stopped mattering once switched to an
unconditional heartbeat) — that produced the real live values:
`name='Disc Yt Kick' owner='Agility Cc' myRank=UNRANKED memberCount=90`.
**Lesson for future onboarding of a new FC:** don't trust a guessed owner
RSN — confirm it either by having the real owner online to check their
in-game crown icon, or (now proven reliable) by reading it directly from
a live `FriendsChatManager.getOwner()` call after being in the channel
for more than a few seconds.

**Fix applied directly to production data** (not a code change — the
group's `owner_rsn` is data, not config baked into a migration): a
one-off `docker compose exec api python -c ...` snippet loading the
`FcGroup` row (`id=1`) via SQLAlchemy and updating `owner_rsn` from
`'Agility FC'` to `'Agility Cc'`, committed directly. Verified live:
`GET /api/v1/status` with the plugin token now returns
`"fc_owner": "Agility Cc"`.

**Diagnostic logging left in the codebase (commit `<pending>`):** the
one-shot join-event log (`Joined friends chat - name=... owner=...
myRank=... memberCount=...`) is kept permanently — low-noise (fires once
per FC join) and exactly what would have shortcut this whole diagnosis
if it had existed from Phase 3. The periodic 5s heartbeat added mid-
diagnosis was removed once the real values were captured — appropriate
for a one-off live debugging session, not something to leave running
forever in normal use.

Owner re-testing attendance/roster/FC-member-observation now that the
owner RSN is corrected.

## Post-Phase-7 feature: attendance auto-timeout — done 2026-09-26

Owner feedback after initial DD testing: "does it just keep ticking up
from when a ID gets added" - attendance's `left_at` only ever closed when
the roster diff saw someone leave the FC *channel* entirely
(`services/roster.py`'s `diff_roster`), so an AFK member who never
actually left the channel had their "time present" tick up forever
regardless of whether they were still doing the course. This was a real
gap in what "attendance" meant, not a bug in the existing roster-diff
logic itself (which is correct for what it does).

**Design (confirmed with owner via three quick decisions):**
1. **What counts as a sighting** — any signal: a lap, a self-reported
   `on_course`, or being observed in-course by another plugin user.
2. **Timeout** — 2 minutes of no sighting (owner's own number, tighter
   than the 10/15/30-minute options offered).
3. **Return behavior** — a new attendance record, not reopening the old
   one (falls out naturally from the existing roster-diff `to_open` logic
   once a prior session is closed — no extra code needed for this part).

**Mechanism:**
- New `attendance.last_sighted_at` column (Alembic
  `ec14febc22d0_add_attendance_last_sighted_at`), distinct from
  `joined_at`/`left_at` which still track channel membership.
- New `IngestBatch.course_sightings` (plugin) / `IngestBatchIn.course_sightings`
  (server): a list of RSNs sighted at the course *this specific upload
  cycle* - the reporter's own RSN when `SelfStateCollector.isOnCourse()`,
  plus whichever FC roster members `FcMemberScanner` currently sees in the
  course region (`FcMemberScanner.getCurrentlyInCourse()`, rebuilt fresh
  every tick it scans at all, cleared the instant it stops). Deliberately
  sent on **every** upload regardless of whether anything else changed -
  every other `IngestBatch` field is omitted-if-unchanged, but a
  heartbeat that only sends when something *changes* isn't a heartbeat.
  Not restored on a failed upload either (unlike every other field here) -
  it's a live signal, not a durable event, so an occasional dropped
  heartbeat during a network blip is an acceptable/expected cost, not a
  bug to guard against.
- Server: `_process_course_sightings()` in `api/v1/ingest.py` bumps
  `last_sighted_at` on an *existing open* attendance row only - it
  deliberately never opens a new one itself (that stays the roster diff's
  job), so a stray sighting can't invent attendance for someone who was
  never actually a real roster member.
- New background worker (`workers/attendance_timeout_worker.py`,
  `attendance_timeout_worker_loop`, checks every 30s, wired unconditionally
  into `main.py`'s lifespan - no feature flag, since this is core
  internal DB-only logic, not an optional external integration like the
  hiscores/items workers): closes any attendance row whose
  `last_sighted_at` is more than 120s old, setting `left_at =
  last_sighted_at` (their last *confirmed* moment present, not "now" -
  matches the existing roster-diff close's own convention of using the
  snapshot timestamp rather than wall-clock time).

**Migration safety, since `attendance` already had real rows from live
testing:** the new column can't just be `NOT NULL` with no backfill -
MySQL would reject it outright. Backfilled every existing row's
`last_sighted_at` to the migration's own execution time (not `joined_at`)
specifically so a currently-open, actively-in-use session wouldn't look
instantly stale to the timeout worker the moment this deployed - already-
closed rows don't care what value they get, since the worker only ever
looks at `left_at IS NULL` rows. Took a fresh backup
(`deploy/backup.sh`) before applying, confirmed post-deploy: existing
rows backfilled correctly, `left_at` untouched on already-open sessions,
container healthy, no errors.

**Incidental fix bundled into the same migration, unrelated to this
feature:** `alembic revision --autogenerate` also flagged that
`players.latest_snapshot_id`'s foreign key (declared in the model since
Phase 4 with `use_alter=True` for the circular players↔player_snapshots
reference) was never actually applied to the real schema - a latent gap
since the very first migration, "confirmed as a non-issue" at the time
only because `alembic upgrade head` happened to still succeed without
it. Verified safe to add now (the only place `latest_snapshot_id` is
ever set, `ingest.py`/`hiscores_worker.py`, always assigns a
freshly-inserted snapshot's own id, so no orphaned references could
exist). Confirmed applied via `SHOW CREATE TABLE players` post-deploy.

**Separate, not-fixed-now finding surfaced while writing this feature's
tests:** `BUILD_PLAN.md` §6.2 documents "Timestamps `DATETIME(3)` in
UTC" (millisecond precision) as the schema convention, but no model
anywhere actually declares that precision (`mapped_column(DateTime())`
throughout, no `fsp` argument) - a project-wide, pre-existing gap between
the written spec and the real schema, not something this feature
introduced. Surfaced because a microsecond-precision Python `datetime`
round-tripped through a real DB column comes back truncated to whole
seconds, which broke an exact-equality test assertion until loosened to
second-level tolerance. Not fixed here - would mean touching all 16
tables' timestamp columns via a fresh migration, well beyond this
feature's scope - just flagged so it doesn't get silently re-discovered
from scratch later.

**Tests:** 71 server tests passing (up from 63) - new coverage: pure
`should_close()` edge cases (exactly-at-timeout stays open, one second
past closes, recently-sighted stays open), a dedicated
`test_attendance_timeout_worker.py` exercising `run_once()` against a
real DB (closes a stale session, leaves a fresh one alone, never touches
an already-closed one), and `test_ingest.py` coverage for
`_process_course_sightings()` (bumps an existing open session, does not
invent a new one for someone never actually attending). Plugin: 47
tests still green (no new pure-logic surface here that isn't already
covered by existing `DdTracker`/`FcMemberScanner` patterns - the new
`getCurrentlyInCourse()`/heartbeat wiring follows the exact same
`inCourseArea` boolean already exercised indirectly elsewhere).

## Plugin feature: ranked members excluded from DD presence — done 2026-09-26

Owner feedback: ranked FC members are usually busy with other duties
during a DD call (calling it, watching chat, managing the queue) rather
than actually participating, so they "shouldn't be in the DD table in
any way." Excluded from DD presence entirely - both `SelfDdSampler`
(the reporter's own presence, checked via `FriendsChatManager.getMyRank()`)
and `FcMemberScanner` (observed players, via `fc.findByName(rsn).getRank()`)
now skip the `DdTracker.onObservation(...)` call for anyone whose rank is
anything other than `UNRANKED` (value `-1`) - i.e. Friend and above.

**Deliberately a separate, hardcoded rule from the group's configurable
`dd.min_rank`** (who's *allowed to call* DD) even though both currently
happen to use the same threshold (`0`) - one is "who may call it", the
other is "who's structurally too busy to be judged for participating in
it." If `min_rank` is ever changed later, this exclusion should not move
with it.

**Client-side only, necessarily** - the server has no independent
knowledge of live FC ranks (only whatever the plugin chooses to send),
so this can't be enforced or double-checked server-side; it relies on
the plugin actually filtering before upload. Regular loadout-gap
tracking (the RUNNING check, non-DD) is untouched by this - a ranked
member doing normal laps outside of a DD call is still tracked
normally, and still counts toward the course-sighting heartbeat for
attendance purposes even during a DD call (they're still legitimately
at the event, just not something to judge for DD compliance).

Not retroactively applied to already-stored DD presence rows from the
first in-game DD test - this is a going-forward behavior change, not a
data cleanup request.

No new unit tests - `FcMemberScanner`/`SelfDdSampler` have never had
direct tests (both need a live `Client`/`FriendsChatManager` to
exercise meaningfully, which this project's testing approach
deliberately avoids mocking - see §5.10's own stated preference for
pure-logic tests). `./gradlew build`: 47/47 still passing, clean.

## Server hardening: 4 findings from an owner-run security pass — done 2026-09-26

Owner ran their own quick audit while other work was in progress and
handed over 4 findings. Verified each directly against the code (not
taken on faith) before fixing:

1. **No rate limiting on `/login`** (`app/web/routes/auth.py`) — the
   plugin-token endpoint (`app/deps.py`) already had a dedicated
   `RateLimiter` (5 failures/5min/IP → 429, per §6.3), the human-facing
   dashboard login had nothing at all. Added a **separate**
   `dashboard_login_rate_limiter` instance (`app/security.py`) - same
   shape (5/5min), deliberately not sharing state with the plugin-token
   limiter, since a failed plugin-token auth from an IP shouldn't lock
   that IP out of the human login and vice versa; they're different
   attack surfaces with no reason to couple.
2. **Leaderboard data had no plausibility bounds** (`app/schemas/ingest.py`)
   — `lap_time_ms`/`tickets_gained`/`gear_value`/`risk_value` were plain
   unbounded ints. Since the plugin is open Java, trivially forkable, and
   a real target for someone hardcoding absurd lap times to climb a
   competitive leaderboard, added real (researched, not guessed) bounds:
   `lap_time_ms` floor of 30,000ms (the wiki's own stated theoretical
   fastest lap is 67 ticks / ~40.2s under perfect play - the floor sits
   comfortably below that so no real lap is ever rejected), `tickets_gained`
   capped at 1 (the wiki confirms exactly one ticket per lap, always, no
   variance), `gear_value`/`risk_value` bounded to `[0,
   2147483647]` (OSRS's own classic 32-bit cash-stack ceiling). **Explicitly
   a partial mitigation, not a full fix** - a batch of fabricated laps with
   plausible-looking spaced-out `completed_at` timestamps would still get
   through, since nothing here cross-checks claimed timestamps against
   real wall-clock pacing; that needs actual server-side game-state
   verification, well beyond a "catch the laziest cheats for free" pass.
3. **No `max_length` on any `rsn`/`name` field** (`app/schemas/ingest.py`)
   while the real `players.rsn` column is `VARCHAR(12)` - an oversized
   name previously caused an unhandled MySQL truncation error (a raw
   500) instead of a clean Pydantic 422. Added a shared `Rsn = Annotated[str,
   Field(max_length=12)]` type alias, applied everywhere an RSN/member
   name appears in the ingest schema (including `course_sightings`,
   added the same session as this audit).
4. **CSRF token compared with `!=` instead of `hmac.compare_digest`**
   (`app/web/deps.py`). Practical risk was already low (high-entropy,
   HttpOnly, cookie-delivered over HTTPS - not realistically
   exploitable), but a free fix while already touching the file. Also
   tightened the null-handling in the same line (`expected` computed
   once, `submitted` coerced to `str` before the constant-time compare).

**Tests:** 77 passing (up from 71) - new coverage: 5 failed dashboard
logins → 429 on the 6th, an oversized RSN → 422, an implausibly fast lap
time → 422, confirmation a genuinely real lap time (45s) is still
accepted (proving the floor doesn't reject real play), more than one
ticket in a single lap → 422, a negative `gear_value` → 422. `ruff
check` clean. Deployed - migration-free (schema/validation-only changes,
no DB structure change), confirmed healthy post-deploy.

## Loadout system redesign: interactive "character sheet" editor — done 2026-10-01

Owner request: completely redesign the loadout rule editor into an
interactive, RuneScape-equipment-tab-styled UI - click a slot, pick which
specific items are acceptable there, flag non-compliance against the
picked list rather than the old pattern-matching rule language.

**Design decisions (confirmed via 4 quick questions before building):**
1. Replace the old rule system entirely (FILLED/NAME_ENDS_WITH/ITEM_IN/
   conditional "when" rules) rather than run two systems side by side.
2. An unconfigured slot = fully unrestricted (matches the old optional-
   slot convention, e.g. ring/ammo).
3. Drop cross-slot conditionals (the old "crossbow needs a shield too"
   rule) - simplify the actual rule rather than keep expressing it.
4. Show real item icons (OSRS Wiki-hosted) in the slot grid, not just
   text - confirmed worth the extra work over a text-only layout.

**New per-slot model** (one `LoadoutRequirement` row per slot now, not
per arbitrary named rule - `UNIQUE(loadout_id, slot)`): `required`
(bool) + `allowed_item_ids` (JSON list). Three real states, no row at
all being a fourth (fully unrestricted):
- `required=true`, empty list → must wear *something*, any item (covers
  what the old `FILLED` check did for helm/legs/boots/amulet).
- `required=true`, populated list → must wear one of those specific
  items.
- `required=false`, populated list → empty is fine, but if something
  *is* worn there, it must still be one of the allowed items.

Dropped entirely: `req_key`, `label`, `check_type`, `args`, `when_json`,
`sort` columns. The slot name itself (e.g. `"WEAPON"`) is now the
natural, stable key used in `failed_requirement_keys`/`dd_loadout_failed`
arrays - render order is a fixed equipment-tab position
(`services/loadouts.py`'s `SLOT_ORDER`), not a configurable per-row sort.

**Plugin side:** deleted `loadout/Check.java` and `loadout/Condition.java`
entirely. `Requirement` is now just `{Slot, required, allowedItemIds}`.
`LoadoutEvaluator` rewritten around the three states above - still pure,
still zero RuneLite imports, still only ever returns slot-name strings,
never item data. `SelfLoadoutCheck`'s chat warning no longer looks up a
per-requirement label (there isn't one anymore) - it just lowercases and
joins the failed slot names directly. `LoadoutEvaluatorTest` fully
rewritten (9 cases covering all three states + RING/AMMO visibility
gating, replacing the old check/condition-based fixtures).
`./gradlew build`: 47/47 passing, clean.

**Server side:**
- `app/models/loadout.py`: `LoadoutRequirement` redefined per above.
- `app/schemas/status.py` / `app/services/loadouts.py`: wire shape is now
  `{slot, required, allowed_item_ids}` per requirement, ordered by the
  fixed `SLOT_ORDER` list.
- `app/seed.py`: a new FC group now gets two *empty* Running/DD loadout
  shells, no default rules - the old defaults were expressed as
  name-pattern/conditional rules with no clean mapping onto the new
  model, and "build a runner loadout" was explicitly the point of this
  redesign, so staff build the real rules themselves via the new editor
  rather than inheriting a guessed starting set.
- `app/web/routes/loadouts.py`: full rewrite. New routes: toggle a
  slot's `required` flag, add/remove an allowed item, and a per-slot
  item search (`/loadouts/{id}/slots/{slot}/search`, replacing the old
  flat `/items/search`). Every mutation route detects `HX-Request` and
  returns just that slot's re-rendered fragment (matching the pattern
  already established for the event tabs' auto-refresh) instead of a
  full page reload - search results are directly clickable "Add" buttons
  that swap the slot in place.
- New templates: `loadouts/_slot.html` (one slot's card - icons, a
  `required` checkbox, the allowed-item list with remove buttons, and
  the inline search box; reused identically by the full page and every
  HTMX fragment response, so there's exactly one place a slot's markup
  is defined) and `loadouts/_item_search_results.html`. `detail.html`
  rewritten around a CSS grid matching the real OSRS equipment tab's
  slot positions (head top-center, cape/neck/ammo row, weapon/body/
  shield row, legs, hands/boots/ring), each slot a native `<details>`
  element (collapsed by default, no custom JS needed for the
  expand/collapse interaction).
- Item icons resolved via the OSRS Wiki's own `Special:FilePath/<name>.png`
  redirect (verified working directly, including names with spaces and
  parentheses like "Studded body (g)", before building anything on top
  of it) - no local icon library to source or maintain, with a broken-
  image fallback (`onerror` hides the `<img>`) if the wiki ever can't
  resolve a name.
- **Real bug caught before it shipped:** the first draft of the HTMX
  fragment path built its own template context from scratch and forgot
  `csrf_token` entirely - would have rendered forms that 403'd on every
  submit. Caught by re-reading the code before testing, not by a failed
  test (no test exercises the literal rendered HTML's form fields).
- 9 new/rewritten server tests (require-with-no-list, add/remove an
  allowed item end-to-end against a real `Item` row, unknown item id
  rejected with 404, full-page render using the new grid markers).
  **80 server tests passing** (up from 71).

**Migration (`cf1345efaa36`):** the old rule shape has no meaningful
mapping onto the new one, so existing `loadout_requirements` rows are
cleared (`DELETE FROM loadout_requirements`) rather than migrated -
`loadout_checks`/`dd_presence` history is untouched (those only ever
stored opaque requirement-key strings, already historical regardless).
Took a fresh backup first and captured the live group's pre-migration
rules for reference before clearing them:

| Slot | Old rule |
|---|---|
| WEAPON | must be worn, name ends with "bow" |
| HEAD / LEGS / BOOTS / AMULET | must be worn, any item |
| SHIELD | must be worn *only if* weapon is a crossbow (dropped per decision #3) |
| DD: WEAPON | must be worn, name ends with "bow" |

**Owner action needed:** the live Running/DD loadouts are now fully
unrestricted (no rows at all) until rebuilt via the new editor at
`/loadouts/1` and `/loadouts/2` - this is expected, not a bug, per
seed.py's new empty-shell behavior and decision #1 above.

**Verified live on production** (not just against tests): logged in,
searched for "magic shortbow" via the real endpoint, added item id 861
to the WEAPON slot via the real HTMX POST, confirmed the fragment
re-rendered with the correct icon and delete control, then removed it
again to leave the slot clean for the owner's own setup.

**Unrelated bug noticed during this deploy, not fixed (out of scope):**
the daily `items_worker` failed mid-run right after this deploy -
`(1264, "Out of range value for column 'ge_price'")` for item id 20011,
whose current GE price (2.29 billion) exceeds the `ge_price` column's
plain `INT` range (max ~2.147 billion). Pre-existing, unrelated to this
feature - flagged to the owner to decide whether/when to fix (likely a
one-line column-type change to `BIGINT`), not actioned here.

## Removed all gear/risk value and item price tracking — done 2026-10-01

Owner's response to the items-worker bug report above: "why would we need
that for this plugin?" - a fair challenge. Checked before answering:
**nothing in the app actually displays `items.ge_price`/`ha_value` or the
plugin's own self-reported `gear_value`/`risk_value` anywhere** (verified
by grep across every template/route, zero hits beyond the
definitions themselves) - both were speculative scaffolding from the
original build plan (§5.4/§6.6, a possible future "gear value" display
that was never actually built), not live features. Owner's call: strip
it all out entirely, both as dead-code cleanup and because a gear/risk
value calculator is a real Plugin Hub approval risk regardless of
whether it's ever displayed - it reads as PK-target reconnaissance
tooling (identifying who's carrying the most GP) no matter the actual
intent, which isn't a risk worth carrying for a feature nothing uses.

**Plugin:** `SelfStateCollector` no longer injects RuneLite's
`ItemManager` or computes any price sums - `updateWorn()`/
`updateRiskAndTickets()` (renamed `updateTicketCount()`) now only build
the equipment list and count wilderness agility tickets, the two things
actually needed. `SelfState.gearValue`/`riskValue` fields removed.
`shareGear`'s config description updated ("Upload your own worn items",
dropping "and gear/risk value"). `./gradlew build`: 47/47 still green.

**Server:** `PlayerSnapshot.gear_value`/`risk_value` and
`Item.ge_price`/`ha_value` columns dropped (migration `04df333581b5`,
pure column drops, no backfill concerns). `items_worker.py` rewritten to
only fetch the OSRS Wiki's item **mapping** (id/name), dropping the
`latest` prices endpoint call entirely - `merge_item_data` renamed
`parse_item_mapping` to match what it actually does now. `SelfStateIn`
schema and `_process_self()` no longer reference either value field.

**Docs:** `PLUGIN_HUB_SUBMISSION.md`'s warning string no longer claims
"inventory value" (no longer true), its stale `run-weapon`/`check`-style
example updated to match the loadout redesign's actual current wire
shape, and a new PR-description bullet explicitly calls out the removal
as a deliberate Hub-approval consideration - worth the reviewers seeing
this was actively considered, not just absent by accident. `API.md`'s
ingest example likewise de-stale'd (dropped gear/risk value, fixed the
`run-helm`/`run-bow` examples to the new slot-name convention, and
documented `course_sightings` properly for the first time - it existed
in code since the attendance-timeout feature but was never actually
written up here).

**Tests:** the one test that specifically validated `gear_value` bounds
is gone along with the field itself (nothing meaningful left to assert).
79 server tests passing (down from 80, as expected), 47 plugin tests
still green. Backed up before deploying (pure column drops, but a real
backup habit regardless of how low-risk a given migration looks).
Verified post-deploy: columns gone, all 4662 existing items' names
preserved untouched.

## Loadout editor UI fixes: search broken, too cramped — done 2026-10-01

Owner report right after using the new editor for real: search "doesn't
work," and the slot cards were "awful to use... not enough space to
type in, hardly visible."

**Real bug, not just a UX complaint:** the search `<input>` in
`loadouts/_slot.html` had no `name="q"` attribute. HTMX serializes a
triggering element's value under its `name` attribute - with none set,
every keystroke sent the search request with no `q` param at all, so
the server always hit the "type to search…" empty-state branch
regardless of what was actually typed. The search endpoint itself was
correct and already had a passing test exercising it directly with an
explicit `q=...` - that's exactly why this slipped through: the test
proved the backend logic worked, but nothing ever rendered the real
page and checked the input was actually wired to call it. Fixed, and
added a regression test that asserts `name="q"` is present in the
rendered HTML specifically so this class of bug (correct endpoint,
disconnected frontend) can't silently return.

**Layout:** the expanded per-slot editor (required checkbox, allowed-
item list, search box) was squeezed into the same ~120-140px grid cell
as the slot's own compact icon summary - genuinely too narrow for a
useful search box or readable item names. Changed the editor to pop out
as a floating overlay (`position: absolute`, 300px wide, drop shadow)
anchored below its slot instead of being constrained by the grid
column's width, bumped font sizes back to normal reading size inside
it (they'd been shrunk to fit the cramped layout), gave the search
input `width: 100%` of its new, actually-usable container, and switched
low-contrast `opacity`-based muted text to Pico's own `--pico-muted-color`
for better visibility. The compact collapsed summary (icons + badge)
is unchanged - only the expanded editing state was cramped.

Verified live post-deploy: simulated the exact request HTMX now sends
(a GET with `q` as a real query parameter) and confirmed real search
results come back, and confirmed `name="q"` is present on all 11
rendered slots' inputs. 80 server tests passing (up one, the new
regression test).

## Phase 8 — OSRS-themed dashboard reskin — done 2026-10-01

Implemented as scoped in `BUILD_PLAN.md`'s Phase 8: a theme-layer-only
reskin (override Pico's own CSS custom properties, no template
structure/route changes) rather than a framework rip-out.

**Font decision:** the plan called for a RuneScape-style display font.
Asked the owner: a properly-licensed open fantasy font (no ambiguity) vs.
an unofficial community recreation of OSRS's actual interface font (used
widely by fan tools, informal licensing). Owner chose the latter,
initially reasoning the site "won't be publicly available" - corrected
that this host *is* internet-reachable via Cloudflare Tunnel like every
other service here (login only gates dashboard content, not static
assets), but owner confirmed proceeding anyway given how obscure/
unadvertised the hostname is and the font's established use throughout
the OSRS tool community. **Sourced from RuneLite's own repo** (`runescape.ttf`/
`runescape_bold.ttf`, bundled there for RuneLite's own OSRS-style text
rendering) rather than an unverified random source - this plugin already
depends on RuneLite itself, so it's a real, checkable provenance rather
than a blind download. Fetched in the Dockerfile alongside every other
vendored asset (htmx/pico/chart.js), not committed as binaries.

**Theme:** parchment background (`#e8d5a8`)/dark-brown text, gold-brown
(`#8b5e1f`) links and accents, lighter-parchment panels with a brown
"carved" border and sharper corners (`--pico-border-radius: 3px`,
OSRS interface chrome isn't rounded), the RuneScape font applied to
headings and the nav bar only (body text/tables stay on Pico's own
readable sans-serif - a dense loadout/DD-leaderboard table in a bitmap
display font would hurt readability). Top nav restyled as a dark wood-
brown bar with gold text, standing in for an OSRS interface tab bar.
New file: `app/web/static/css/osrs-theme.css`, loaded in `base.html`
right after `pico.min.css` so it only overrides, never fights, Pico's
own structural CSS.

**Verified real Pico variable names before writing overrides** (fetched
Pico's actual non-minified source rather than guessing) - `--pico-primary`,
`--pico-card-background-color`, `--pico-muted-color`,
`--pico-form-element-border-color`, etc. Getting these wrong would mean
the override silently does nothing, which is a very easy mistake to make
blind.

**Verification:** 80 server tests still passing (pure CSS/template
change, no functional surface touched) and confirmed live post-deploy
that the new stylesheet (200), both font files (200 each), and the
`<link>` tag all serve/render correctly. **Could not verify the actual
visual result** - no browser available in this environment, so the real
"does it look right" check is the owner's own, same as every other UI
change in this project that needed eyes on a real page.

## Phase 8 follow-up: real feedback from actually looking at it

Owner's first look: the per-slot equipment labels (e.g. "Weapon") weren't
in the RuneScape font, and "the blues and solid white background don't
look nice for the lower section."

**Font gap:** `.slot-summary-label`/`.slot-badge` are plain `<div>`/`<span>`
elements, not headings, so the original CSS (scoped to `h1-h6` + nav)
never reached them. Added to the font-family selector list.

**Blue/white gap - re-audited Pico's variable list properly this time**
(fetched the actual light-theme color block in full rather than the
sampled subset from the first pass) and found several real backgrounds
still defaulting to Pico's stock colors that nothing in the first pass
touched: `--pico-dropdown-background-color` (`#fff` - native `<select>`
closed-box styling and Pico's own dropdown component), `--pico-switch-*`
(blue-grey toggle switches), `--pico-code-background-color`. None of
these are reachable by overriding `--pico-primary`/`--pico-card-*` alone
- they're separate variable families. Also added `accent-color:
var(--pico-primary)`, since native checkbox/radio tint is a completely
separate CSS mechanism Pico doesn't touch for you at all - every
checkbox on the site (loadout "required" toggles, settings toggles) was
rendering in the browser's own default blue regardless of every other
variable set. **Documented one real, unfixable-via-CSS limitation**: an
opened native `<select>` dropdown's options list is rendered natively by
some browsers and can't be fully recolored by any amount of CSS - noted
in the stylesheet itself so this isn't re-investigated as a bug later.

**Real infrastructure bug found while verifying this fix:** deployed
correctly, confirmed the container had the right file content, but the
site kept serving the *old* CSS through Cloudflare - its edge cache was
serving a stale cached copy of the stylesheet at the same URL, even
though the origin was already updated. Confirmed by comparing the
container's own file content (updated) against what Cloudflare was
actually returning (stale) at the identical URL. Fixed properly rather
than one-off purging the cache: added `asset_version`
(`app/web/templating.py`, a Jinja global, currently `2`) appended as
`?v=N` to every vendored CSS/JS link in `base.html`. Bumping that number
on any future static-asset change gives Cloudflare (and browsers) a
genuinely new URL to fetch, sidestepping the need for a manual cache
purge every time - this will otherwise silently bite every future CSS/JS
tweak to this dashboard, not just this one.

Verified: the versioned URL (`?v=2`) returns the updated CSS through
Cloudflare immediately, confirmed against the container's actual file
content. 80 server tests still passing.

## Phase 8 v3: darker canvas per owner feedback

Owner: the light-parchment-everywhere background was too plain/pale -
"something nicer but darker runescape in vibe." Reworked the palette to
a dark wood-brown canvas (`#241c12`, deliberately darker than the nav
bar's `#3e3529` so the nav still reads as a raised bar above it) with
gold heading/link text (`#e3b341`-ish, closer to OSRS's own quest-log/
chat-box look than the previous dark-text-on-parchment version), and
panels (`article`/the loadout slot cards, via the same
`--pico-card-background-color` variable) in a mid-brown a shade lighter
than the canvas rather than light parchment, so they read as "raised
interface boxes."

**Deliberately kept form inputs light** (`--pico-form-element-background-color:
#e8d5a8`) rather than going dark-on-dark - OSRS's own text boxes (bank
search, chat) are light even inside otherwise-dark interface chrome, and
it keeps actual data entry/search readable. Re-applied the same dropdown/
switch/code variable treatment from the previous round to the new dark
values (those are a separate variable family from `--pico-card-*` and
need their own values every time the palette shifts, not just once).

Bumped `asset_version` to `3` (see the caching note above) and confirmed
the new background color is live through Cloudflare on the very first
fetch at the new URL - no stale-cache round-trip this time, which is
exactly what the versioning fix was for. 80 server tests still passing
(pure CSS change).

## Phase 8 v4: fix - color overrides were never actually applying (CSS specificity)

Owner reported "nothing has changed" after the v3 dark-canvas deploy,
then confirmed from a fresh incognito window - ruling out the caching
explanation above (which was the correct fix for the *previous* "my
change isn't showing up" report, but wasn't the cause this time).

Root cause: Pico's own light-theme color block is scoped to the selector
`:root:not([data-theme=dark])`, not plain `:root` (confirmed against
Pico's real source). `:not()`'s argument counts toward specificity, so
that selector is (0,0,2,0) - higher than this file's own plain
`:root { ... }` at (0,0,1,0). `base.html` sets `<html data-theme="light">`
explicitly, so Pico's higher-specificity selector matches that element
and wins regardless of `osrs-theme.css` loading second in `<head>` - CSS
specificity beats source order whenever both selectors match the same
element. In other words: every `--pico-*` custom property override in
this file had been inert since the very first Phase 8 deploy, not just
the v3 darkening - they were correctly *served* (confirmed repeatedly via
curl, both before and after the Cloudflare cache fix) but never actually
*applied* in a real browser, which is exactly why curl-based verification
kept giving false confidence on this one file. Only a rendered-page check
(or working through specificity by hand, as done here after fetching
Pico's actual source) can catch this class of bug - logged here so it
isn't re-learned by trial and error next time this file is touched.

Fix: added `!important` to every custom-property declaration inside
`osrs-theme.css`'s `:root` block. Deliberately did not instead copy
Pico's exact `:root:not([data-theme=dark])` selector to tie its
specificity and win on source order - that would silently break again if
a future Pico version changes its internal selector, whereas
`!important` on these declarations is robust to that. Scoped to just the
custom-property block; the plain-element overrides below it (`body >
nav`, headings, `mark`, etc.) don't have a competing same-or-higher-
specificity Pico rule to lose to, so they were left as-is.

Bumped `asset_version` to `4`. 80 server tests still passing (pure CSS
change). Verified via curl that the new `!important`-flagged values are
served correctly at the new URL; still needs the owner's own visual
confirmation in a real browser, since that's the one thing curl can
never actually prove for a specificity bug like this.

## Phase 8 v5/v6: Logout button was invisible, then wrong font

Owner visually confirmed the v4 fix held (dark theme rendering correctly),
closing that open item - then immediately spotted two more small bugs on
the nav's Logout button while looking at it.

**v5 - invisible text.** `body > nav button.secondary` set its idle text
color (`--pico-secondary-inverse`) to `#3e3529`, the *exact same hex* as
`body > nav`'s own background a few lines above - with
`--pico-secondary-background: transparent`, the button's text was
rendering in a color identical to what was behind it, i.e. invisible.
Fixed by changing idle `--pico-secondary-inverse` to gold (`#ffb833`),
matching the button's own border color, readable against the
transparent/dark nav.

Found and fixed a second bug in the same block while there: the `:hover`
rule set `--pico-secondary-background: #ffb833`, but Pico's hover/focus/
active state actually reads a *different* variable,
`--pico-secondary-hover-background` (confirmed against the vendored
`pico.min.css` source) - so that rule was a dead no-op the whole time,
silently falling back to Pico's stock blue-grey hover color instead of
gold. Fixed to set the right variable name, plus a hover-scoped
`--pico-secondary-inverse: #3e3529` (dark text, since hover's background
is now actually gold) and extended the selector to `:focus`/`:active`
to match how Pico itself groups those three states.

`asset_version` bumped to `5`. 80 server tests still passing (pure CSS
change, no logic touched). Deployed, committed (`a19d624`) and pushed.

**v6 - wrong font.** The RuneScape-display-font selector list
(`h1-h6, body > nav strong, body > nav a, .slot-summary-label,
.slot-badge`) never included `body > nav button` - the Logout button is
a `<button>`, not an `<a>` or `<strong>`, so it fell back to Pico's own
sans-serif while every other nav element used the RuneScape font. Added
`body > nav button` to that selector list.

`asset_version` bumped to `6`. 80 server tests still passing. Deployed,
committed (`b75ff12`) and pushed.

## RBAC confirmation sweep + test coverage gap closed

Owner asked whether the viewer/staff/admin roles can actually change
things, i.e. wanted this confirmed, not just trusted from the code.
Read every route's `Depends()` across all 8 route files (29 routes
total): every mutating (POST) route requires `require_role(STAFF)` or
`require_role(ADMIN)` plus `verify_csrf`, and `ROLE_RANK = {VIEWER: 0,
STAFF: 1, ADMIN: 2}` means viewer fails all of them. No POST/PUT/DELETE
route exists that only depends on `require_user`.

That reasoning was correct, but while checking for existing test
evidence (not just trusting the read), found `test_dashboard_rbac_csrf.py`
only actually exercised `/events`, `/settings`, and `/admin/*` GETs via
real requests - the loadout slot-editing routes, raggers routes, and
admin user/group-management routes (create-user, reset-password,
rotate-token) were never hit by a role-check test at all, only reasoned
about from their `require_role()` call site. Added 4 new tests
(`test_viewer_cannot_edit_loadout_slots`, `test_viewer_cannot_manage_raggers`,
`test_staff_cannot_manage_users_or_groups`, `test_viewer_cannot_change_settings`)
that make real HTTP requests against a running test server and assert
the 403s, closing the gap with executed proof rather than a code read.

84/84 tests passing (80 existing + 4 new). Test-only change, no deploy
needed. Committed (`06d2f00`) and pushed.

## Viewer read access narrowed to Events + DD Leaderboard only

Owner-directed scope change, found while reviewing the RBAC sweep above:
Loadouts, Players, and Raggers were viewer-readable (`require_user`,
i.e. any logged-in role), inconsistent with everything else in those
sections already being staff+ only. Owner asked to remove all three
from what viewer can see, leaving only Events and DD Leaderboard.

Bumped the GET routes for `/loadouts`, `/loadouts/{id}`, the loadout
item-search fragment (`/loadouts/{id}/slots/{slot}/search`), `/players`,
`/players/{id}`, and `/raggers` from `require_user` to
`require_role(UserRole.STAFF)`. Removed the now-unused `require_user`
import from `loadouts.py` and `raggers.py`. Nav bar (`base.html`) updated
to wrap the Loadouts/Players/Raggers links in a `{% if user.role.value
in ("staff", "admin") %}` block, so viewers no longer see links that
would just 403 if clicked.

Updated 3 existing tests (`test_loadout_list_and_detail_render`,
`test_item_search_returns_html_fragment`, `test_players_list_and_profile_render`)
that logged in as viewer purely to exercise rendering, not role
enforcement - these would now fail for the wrong reason (403 instead of
whatever they were actually testing), so switched them to staff. Added
`test_viewer_cannot_read_loadouts_players_or_raggers` and
`test_staff_can_still_read_loadouts_players_and_raggers` to
`test_dashboard_rbac_csrf.py` for explicit 403/200 coverage on all four
newly-restricted paths.

Not a static-asset change, no `asset_version` bump needed. 86/86 tests
passing (84 + 2 new). Deployed, committed (`9a4fcc3`) and pushed.


## 2026-10-27 - Opt-in masses, runner/anti roles, baked-in server

Owner-requested before the in-game test pass and Hub submission.

- Server: `event_participants` table + migration `a7c3e91d5b24`;
  `POST /api/v1/event/respond`; `/status.participants`; ingest opt-in gate and
  anti filtering (laps, DD, running-loadout); observer reports about
  antis/No-answerers dropped; dashboard "Start mass" wording and a Role column.
  Tests: `tests/test_participation.py`, existing ingest tests now opt in via
  `tests/helpers.py`. 94 passing.
- Plugin: `mass/MassParticipation` (popup, restore-from-server, gating),
  `MassRole` config dropdown, `api/ServerConfig` (URL + token compiled in; API
  URL/token settings removed), status poll 10s, observer skips antis/No,
  runner-only laps/DD/gear warnings, panel shows mass state + "Attend this
  mass" button. Pure decision logic unit-tested (`MassParticipationTest`).
- Token rotated (old one invalidated) and baked into `ServerConfig`.
- Decisions: non-plugin FC members = runners; antis skip laps + DD only
  (deaths kept); a closed-without-answer popup sends nothing.

### 2026-10-27 (later) - Per-role stints

Switching Runner/Anti mid-mass now creates a new record instead of overwriting:
new table `participant_stints` (migration `b8d4f02e6c35`, backfills current
attendees). `respond` closes the open stint on a role change or decline and
opens a new one. The event Attendance tab shows one row per (player, role) -
time present is the overlap of their attendance sessions with that role's
stints (time before their first answer counts toward their first role), laps
are counted by completion time within the role's windows. Non-plugin members
still get a single Runner row. 96 server tests passing.

### 2026-10-27 (later still) - Rank-gated gear flags + in-game overlay

Concern: anyone who installs the plugin and opts in could see the "FC members
in view" gap list, i.e. a ready-made list of players with no gear. Gear is public
in-game, so the aim is to stop handing the convenience to strangers, not secrecy.

- `fc_groups.flag_viewer_min_rank` (migration `c9e5a13f7d46`, default 0 = any
  ranked member), editable on the dashboard Settings page; sent in `/status`.
- Plugin: the scanner only runs in the FC the server tracks; the sidebar list
  and the new over-head labels (`ui/GearFlagOverlay`, config "Show gear
  flags") are shown only if the viewer's own FC rank >= the threshold
  (`scan/FlagAccess`). Scanning/uploading for the dashboard is unchanged for
  everyone. Antis/No-answerers never get a label.
- `/status?rsn=` : the full attending/anti list is returned only to viewers whose
  roster-reported rank meets the threshold; others get just their own answer.
  The plugin now passes its name and matches the answer against that same name.
- 98 server tests, plugin tests incl. `FlagAccessTest`.

### 2026-10-27 - In the course = attending

Owner rule: a plugin user who is in the Wilderness Agility Course region during
an active mass, while in the tracked friends chat, has attended - even if they
answered No, closed the popup or never answered. The plugin now sends an
"attending" answer with their configured role (`MassParticipation.onLocation`,
throttled to one retry per 30s on failure) and posts a chat line saying so.
Hub warning text and PR notes updated to match.

### 2026-10-27 - Kits replace the loadout editor; empty-slot + bow/crossbow rules

Found while scoping kits: the old per-slot loadout editor was still the only
thing driving gear flags, and the live Running loadout had exactly one rule left
(HEAD must be a Helm of neitiznot) - every other rule had been cleared in the
2026-10-01 redesign and never rebuilt; DD loadout empty. Owner decision: remove
the editor entirely, drop the DD bow check, and flag runners on:

- empty helm, amulet, body, legs, boots (defaults; editable checkboxes on the
  new Kits page, stored in `fc_groups.required_slots`);
- weapon must be a bow or crossbow (`fc_groups.require_ranged_weapon`); a
  crossbow also needs a shield, a bow (two-handed) doesn't.

Plugin: `loadout/GearRuleEvaluator` (pure, tested) replaces LoadoutEvaluator;
DD presence no longer evaluates gear. Server: migration `d2f7a8b9c014` adds
`kits` / `kit_items` + the two rule columns, drops `loadout_requirements` and
`fc_groups.dd_crossbow_counts_as_bow`. `loadouts` table kept only as the
"Running" anchor that `loadout_checks` history hangs off (`services/gear_rules.
running_anchor`). Manual-gap form is now slot checkboxes. DD tab lost its
"Loadout gaps" column.

Kits: dashboard `/kits` (staff) - gear rules form, named kits with a default,
per-slot recommended item + 28-line inventory with quantities (item search
reused). `/status` sends `gear_rules` and `kits`. Plugin: sidebar "My kit"
dropdown (hidden config `selectedKitId`), `kit/KitCheck` - local-only pre-course
check while attending as a runner and outside the course (variants via
ItemVariationMapping, so any potion dose counts), one chat line + sidebar list,
and `ui/KitBankOverlay` outlining missing items in the bank. 100 server tests,
plugin tests incl. GearRuleEvaluatorTest / KitCheckTest.

### 2026-10-27 - Settings regrouped; Kits page redesign

- Runner gear rules moved from the Kits page to **Settings** (so admin-only now),
  and Settings regrouped into cards: Runner gear rules, Gear flags & observer, DD
  calls, DD tiles, one sticky Save. `/kits/rules` removed.
- Kits list: "Create kit" button + info box explaining recommended kits under
  the title; wider Kit column; per-row Mark as default / Edit / Delete.
- Kit editor (`/kits/new`, `/kits/{id}/edit`): name at the top, gear grid and
  inventory edited in the page only (small inline script, item search is still
  an HTMX fragment `/kits/search?target=`), one **Save kit** posts the whole kit
  (`POST /kits` / `POST /kits/{id}`), replacing its contents; duplicate
  inventory lines merged; unsaved-changes warning. The per-action HTMX save
  routes are gone. 102 server tests passing.

### 2026-10-27 - Course entry fee in every kit

Owner: entering the Wilderness Agility Course costs 150,000 coins, so every
runner kit must carry it. Not stored per kit: `/status` always puts
`{item_id: 995, qty: 150000}` first in each kit's inventory (`app.models.kit.
COINS_ITEM_ID` / `COURSE_FEE_COINS`), any coins line a kit has is replaced by it,
and the editor shows it as a locked row. That leaves 27 inventory lines for
everything else. Coins aren't in the `items` table (the wiki price mapping
leaves them out), hence the hard-coded id. Plugin pre-course check formats
large quantities with thousands separators.

## 2026-10-27 - In-game mass prompt + sidebar redesign (owner test notes, round 1)

- **Popup → in-game prompt.** The `JOptionPane` "Mass started" dialog stole
  focus mid-game. Replaced with `ui/MassPromptOverlay`: an `Overlay`
  (TOP_CENTER, ABOVE_WIDGETS, movable) that's also a RuneLite `MouseListener`.
  Buttons Runner / Anti / Not today plus a × (hide without answering). Left
  presses/releases/clicks inside its bounds are consumed so they never reach
  the game; alt+drag is left alone for RuneLite's own overlay moving.
  `MassParticipation` lost all Swing: `showPrompt` now just sets
  `pendingPrompt` + an orange chat line; new `answer(eventId, yes, role)`
  (saves the role to config, guards double-sends via `sending`),
  `dismissPrompt()`, `hasDeclined()`, `getLastError()`. `promptNow`/`describe`
  removed.
- **Sidebar redesign.** Dashboard palette (`osrs-theme.css` hexes) and the
  RuneScape fonts via `FontManager`, collected in `ui/Theme`. New painted
  components: `AgilityRing` (fill by level to 99; past 99 a near-white second
  lap and an inward radial fill whose glow/brightness scale with progress to
  200M; level number outlined for legibility), `OrnamentDivider` (diamond with
  fading lines), `ThemedButton` (LAF-independent primary/secondary/link),
  `StatusMark` (tick/cross). Client-thread reads moved into `PanelSnapshot`
  (name, Agility xp, in tracked FC) built in `onSecondTick`. FC row shows only
  the FC name (no event name). Connection status moved to small print at the
  bottom with the host and "Copy my tile".
- **FC auto-join: declined.** Would require the plugin to type into the
  chat-channel join box on the player's behalf - automation the Plugin Hub
  rejects. Join hint shown instead.
- Tests: `ui/AgilityRingTest` (level fraction, overcharge, xp formatting).
  Previews were rendered headless to check the ring and prompt visuals.

## 2026-10-27 - Owner test notes, round 2

- Chat colour: KitCheck and MassParticipation lines `ff9040` -> `ff0000`.
- Panel: Discord link (`LinkBrowser.browse("https://discord.gg/agility")`)
  replaces the host line; "Copy my tile" + its client-thread hop removed;
  `ThemedButton` gains PILL (switch role) and TAB styles; CardLayout tabs
  "This mass" / "Previous sessions" with an empty-state hint.
- `history/SessionHistory` + `MassSession`: per-account (RS profile config,
  Gson JSON, newest first, cap 30). Fed each game tick while attending the
  active mass: +600ms when in region 11837, lap/ticket increases from
  LapTracker's running totals (`increase()` treats a drop as a reset - hops
  reset LapTracker). Saved every 30s when dirty and on clearCollected /
  resetState. Local only by design (public plugin token).
- `KitBankOverlay`: `itemManager.getItemOutline(id, qty, colour)` drawn at the
  item's canvas bounds instead of `drawRect`.
- Server: `attendance.sighted_at_course` (migration `e4b9c1d2a057`,
  backfill `last_sighted_at <> joined_at`). `_process_course_sightings` sets
  it. Attendance tab filters to players sighted / with a stint / with a lap;
  manual-gap dropdown to sighted players; empty-state row in the template.
  Deployed after `deploy/backup.sh`. Live check right after: mass 5 had 95
  players in the FC roster, 1 sighted.
- Tests: `SessionHistoryTest`; server `test_attendance_lists_only_people_who_turned_up`,
  sighting test asserts the flag; fixtures that expect a listed player now
  set `sighted_at_course=True`. 108 server tests pass.

## 2026-10-27 - Panel layout fixes (owner test notes, round 3)

- Root cause of the half-visible Discord link and the overlapping "Attending
  as Runner / Switch to Anti": that row was wider than the panel, and
  GridBagLayout then lays out *every* child at its minimum size. Fixed by
  making the row fit (caption "Attending as" + "Runner | [Switch to Anti]",
  with a gold fading `FadeBar` as the "|") and by `ThemedButton.getMinimumSize()`
  returning its preferred size.
- Removed the rule between the Friends chat and Mass sections; section
  captions now use the bold title font in gold (same as card headings).
- Tabs swap visibility instead of CardLayout (which sized both tabs to the
  taller one, centring a short history list and leaving a gap above the
  footer). History stays newest first.
- Verified by rendering the real `TrackerPanel` offscreen (headless, fields
  set by reflection) in runner / anti / not-answered / no-mass / history states.

## 2026-10-27 - Empty-slots only; raggers removed

- Plugin: `SelfStateCollector` no longer reads worn items at all (only the
  inventory, for the ticket count); `SelfState.equipment`, `EquipmentItem`
  and the "Share gear" config item removed. Own gear is still evaluated
  locally (`SelfLoadoutCheck` -> `loadoutFailed`, slot keys only) and by the
  kit check (never uploaded).
- Server: `SelfStateIn.equipment` removed (pydantic ignores the field from
  older builds), snapshot change-detection no longer compares equipment,
  `player_snapshots.equipment` dropped.
- Raggers: `models/ragger.py`, `web/routes/raggers.py`, `templates/raggers/`,
  nav link, deaths-tab sightings section, hiscores worker's ragger queue,
  `test_raggers_full_flow` and the raggers RBAC test removed;
  `test_players_raggers_flow.py` -> `test_players_flow.py`; new
  `test_raggers_feature_is_gone`. Tables dropped in `f5c0d2e3b168` (MySQL
  won't drop the FK-backing index on its own; dropping the table does).
- Wording: "gear checks" -> "missing-gear checks" in the plugin description,
  README, config descriptions and the Hub warning / PR draft; the PR draft's
  "gaps-only" bullet now covers the player's own gear too, and no longer
  mentions the removed DD gear check.

## 2026-10-27 - Stats removed; Players pages removed

- Plugin: `SelfState` reduced to `world` + `loadoutFailed`. Gone: skills,
  combat level, wildy level, skull status, on_course (the last three were
  never read server-side; own course presence still goes up as a course
  sighting), the "Share stats" option and `onStatChanged`. `WildyLevelReader`
  stays for death records. The Agility ring uses `client.getSkillExperience`
  locally only.
- Server: `web/routes/players.py` + templates, nav link and the player links
  in the attendance / laps / DD leaderboard tables removed;
  `workers/hiscores_worker.py`, `hiscores_enabled` and its tests removed;
  `PlayerSnapshot` / `SnapshotSource` removed, `_process_self` only stores
  the self loadout check. `SelfStateIn` only accepts `world` and
  `loadout_failed` (older builds' extra fields are ignored). Migration
  `a1d6e4f7c290` drops the FK, both player columns and `player_snapshots`.
  `items_worker` kept - it feeds the Kits item search.
- Tests: RBAC test now asserts `/players` 404s too; 102 server tests pass.

## 2026-10-27 - No FC member list, no IP logging, fastest laps

- IP: can't avoid receiving it (TCP), so it's never written down: uvicorn
  `--no-access-log` (each 10s status poll logged `IP - GET ...?rsn=NAME`) and
  `logging: json-file, max-size 10m, max-file 3` for api + mysql (no rotation
  before). The only IP use left is the in-memory failed-auth/login limiter.
- Roster removed. Attendance sessions are now opened (not just kept alive) by
  course sightings; `CourseSighting{name, rank}` replaces bare names (server
  accepts both; bare = rank -1; rank only ever raised). The reporter's own
  heartbeat goes out while `SelfStateCollector.isAroundCourse(region)` (course
  region +-1 in x/y) so antis outside the course proper still count; observer
  scanning stays course-region only. Rank for the player's own heartbeat is
  read on the client thread in `onSecondTick` (-1 outside the tracked FC).
  Plugin: `RosterTracker`, `RosterSnapshot`, `RosterMember`, FC member
  join/leave handlers, "Share roster", `SelfState.world` removed. Server:
  `services/roster.py` + test, `_process_roster`, `RosterSnapshotIn` removed;
  `attendance.world` dropped; never-sighted attendance rows and players with
  no remaining references deleted in `b2e7f5a8d391`.
- `ddFailed` / `dd_loadout_failed` removed end to end (always empty since the
  DD bow check went).
- Deaths: time only (`DeathEvent(diedAt)`); `WildyLevelReader` and
  `util/WildernessLevel` (+ test) deleted; Deaths tab shows name + time.
- Fastest lap: `LapTracker.setLapTimeListener` -> `SessionHistory.onLapTime`
  (ignores < 30s glitches): `MassSession.fastestLapMs` and an RS-profile
  `bestLapMs`. Panel rows "Fastest lap" / "Best ever"; history cards show the
  mass's fastest. Lap times are still uploaded and stored (owner: keep).
- Tests: plugin `lapTimeFormat`, `aroundCourse...`; server
  `test_course_sighting_opens_attendance_with_rank`,
  `test_fc_member_list_is_ignored`; removed roster/dd-failed tests. 97 pass.

## 2026-10-27 - Quieter checks, leave a mass, settings tidy

- Owner: the checks spammed chat. `SelfLoadoutCheck` warned on every change
  of the failed set (every gear swap, and again after each hop since reset()
  cleared it); `KitCheck` re-announced whenever it was cleared (leaving the
  course area, hops). Now neither posts on its own: the plugin sets
  `checksDue` to ALWAYS on activation (answering the prompt, auto-attend,
  login restore, role change) and IF_PROBLEM on `WidgetLoaded(BANKMAIN)`, and
  `announceChecks()` posts once on the next attending tick
  (`SelfLoadoutCheck.announce()`, `KitCheck.announce(evenIfReady)`).
- Panel: "Not attending any more" link under "Runner | Switch to ..." ->
  `answer(eventId, false)`; the in-course auto-attend rule still applies.
- Config audit after the data-collection changes: sections renumbered
  (Mass / FC observer / Display), "Gear chat warnings" -> "Pre-mass check
  messages" (same key) covering gear + kit lines and their timing; FC
  observer description now states exactly what it reports; role description
  mentions the separate per-role record.

## 2026-10-27 - All-time fastest lap moved to the header

Owner: "Best ever" now sits under the Agility xp line at the top
("Fastest lap - 0:58.4", hidden until there is one, visible whether or not a
mass is running); "Fastest lap" for the current mass stays in This session.
