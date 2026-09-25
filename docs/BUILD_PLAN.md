# AgilityCCTracker — Build Plan

This document is the **complete, self-contained build specification** for the
Wildy Agility FC Tracker. It is written to be executed by a Claude Code session
running **on the owner's server**, with no access to the conversation that
produced it. Everything you need (requirements, constraints, API names, regexes,
schema, deployment rules) is here.

> **How to use this file (for Claude Code):** work through the phases in order.
> Each phase ends with checks that must pass and a commit. At every 🛑 you must
> stop, report, and wait for the owner's answer before continuing. Keep a short
> progress log in `docs/BUILD_LOG.md` (phase, date, what was done, check results)
> so a later session can resume.

---

## 0. Project summary

A Wilderness Agility **friends chat (FC)** on Old School RuneScape runs organised
group lap events ("masses"). This project tracks those events:

- **Attendance** — who was in the FC during an event and for how long.
- **Attendees' stats, gear, laps, deaths** — full detail for people running the
  plugin (their own data); hiscore stats for everyone else.
- **Loadout compliance** — FC members must wear a required loadout (bow, helm,
  legs, boots, necklace…). The plugin reports *gaps* against the loadout for FC
  members in view, including mobile / non-plugin players.
- **DD tracker** — when staff shout "DD" in FC chat, runners must group up on two
  fixed tiles ("death dotting") until staff shout "break DD". Track who came,
  how fast, how long they stayed, who left early, who kept running, who wasn't
  holding a bow, and a leaderboard of DD time for rewards.
- **Raggers** — hostile non-FC players (PKers). Logged by staff on the dashboard
  by name; stats pulled from official hiscores.

Three components, one monorepo:

1. **RuneLite plugin** (Java 11) — published on the public RuneLite Plugin Hub.
2. **API server** (Python 3.12, FastAPI, MySQL 8.4) — self-hosted on this server
   in Docker.
3. **Web dashboard** (server-rendered Jinja2 + HTMX, same FastAPI app).

The owner already runs a separate app, **GIM Hub (`gimhub-app`)**, on this server
with its own MySQL. This project must be **completely independent of it**: never
modify, stop, restart, reuse the database, network, volumes, or ports of any
existing container. Some layout ideas are borrowed from the GIM Hub self-host
setup, but nothing is shared.

---

## 1. Hard constraints (read before writing any code)

### 1.1 Plugin Hub rules (RuneLite)

Source: `runelite/plugin-hub` README and the RuneLite wiki page
*Rejected-or-Rolled-Back-Features*.

- Plugin must be **Java only** (no Kotlin/Scala), target **Java 11**
  (`options.release.set(11)`).
- **Forbidden:** Java reflection, JNI, running subprocesses, downloading or
  executing code at runtime. "If we cannot review every single line of source
  code that your plugin will execute, we will not accept it."
- Dependencies: only `net.runelite:client` (compileOnly) + Lombok. **OkHttp and
  Gson come from the client — inject them** (`@Inject OkHttpClient`,
  `@Inject Gson`); never `new OkHttpClient()` / `new Gson()` (use
  `gson.newBuilder()` if customisation is needed).
- `runelite-plugin.properties` uses `build=standard`, which means the Hub
  **replaces `build.gradle` and `settings.gradle`** with its own. Do not rely on
  anything custom in those files.
- Resources: load with `getResourceAsStream`, not `getResource`.
- Never block the client thread with network I/O. Game state (`client.*`) is only
  read on the client thread (event handlers already are; otherwise use
  `ClientThread.invoke`). Swing updates via `SwingUtilities.invokeLater`.
- Rejected feature categories that matter here:
  - "Plugins which 'crowdsource' data about other players, such as player
    locations, gear, names, etc."
  - "Plugins which expose player information over HTTP."
  - PvP helpers: level-range indicators, opponent freeze timers, PK/skull
    warnings.
  - Anything that could be abused for AFK agility training.
- Third-party data must be disclosed with a `warning=` line in the plugin-hub
  `plugins/<name>` file (see §9).

### 1.2 What the plugin may upload (agreed design)

The owner discussed this with the RuneLite developers. They confirmed that a
**specific loadout check on FC members is acceptable** (precedent: the
`rogue-chests` Hub plugin) **as long as it reports only gaps against the
required loadout, never what the player is actually wearing**.

| Data | About whom | Allowed to upload |
|---|---|---|
| Stats (xp/levels), combat level, full worn gear, gear/risk value, world, laps, tickets, deaths, own loadout gaps, own DD presence | **Local player only** | ✅ Yes — the user's own data (same model as GIM plugins) |
| FC roster: name, rank, world (from `FriendsChatManager`) | FC members | ✅ Yes — disclosed in `warning=` (precedent: WOM Utils uploads clan member names) |
| Loadout result `{rsn, loadout_id, failed_requirement_ids[]}` | FC members in view | ✅ Yes — gaps only |
| DD presence: durations + booleans (`ms_on_tile`, `left_before_break`, `seen_running`, `arrived_after_ms`, DD loadout gaps) | FC members in view | ⚠ Allowed in code, but **owner must re-confirm with RuneLite devs before Hub submission** (it is derived from location). Must be switchable off server-side (`observer_enabled`) and per user (`fcObserver`). |
| Item ids, item names, coordinates, animations, interactions, combat level, skull of **any other player** | Anyone else | ❌ **Never** |
| Anything about non-FC players (raggers) | Non-FC | ❌ **Never** from the plugin. Raggers are entered by staff on the dashboard. |

Code-structure rules that make this reviewable:

- **Only one class reads other players:** `scan/FcMemberScanner`. No other class
  may call `getTopLevelWorldView().players()`, `client.getPlayers()`,
  `getPlayerComposition()` on a non-local player, or subscribe to
  `PlayerSpawned`, `PlayerDespawned`, `InteractingChanged`, `HitsplatApplied`,
  `AnimationChanged`, `PlayerChanged`.
- `FcMemberScanner` only processes players with `isFriendsChatMember() == true`
  **and** whose name is in the configured FC's roster.
- Raw observations (coordinates, item ids, names of items) never leave
  `FcMemberScanner` → `LoadoutEvaluator` / `DdTracker`. What leaves those
  classes is only requirement ids, durations and booleans.
- A **payload-shape unit test** serialises every upload DTO and asserts that the
  JSON for other players contains no keys like `x`, `y`, `plane`, `itemId`,
  `items`, `equipment`, `location`, `coordinates`.

### 1.3 Server safety rules (this machine)

- Never touch existing containers, networks, volumes, databases, proxy sites or
  cron jobs that are not created by this project. Read-only inspection is fine.
- Compose project name: **`agilitycctracker`**. Own network, own named volume,
  own `mysql:8.4` container.
- Bind the API to `127.0.0.1:<port>` only (default 8090 if free); expose publicly
  only through the existing reverse proxy with TLS, after 🛑 approval.
- Secrets live in `server/.env` (mode 600), never committed. `.env.example` is
  committed with placeholders.
- Ask before: installing system packages, editing reverse-proxy config, opening
  firewall ports, creating cron jobs, anything needing `sudo`.

### 1.4 Git rules

- Work on the branch the owner names in Phase 0 (default: continue on the branch
  this file was delivered on).
- Commit at the end of every phase with a clear message. Push if a remote is
  configured and the owner agreed in Phase 0.
- Never commit `.env`, database dumps, `build/`, `.gradle/`, `__pycache__`,
  `.venv`.

---

## 2. Repository layout (target)

```
/                                   # plugin at repo root so the Hub can build it
├── build.gradle                    # from runelite/example-plugin (see §4.1)
├── settings.gradle                 # rootProject.name = 'agility-cc-tracker'
├── gradlew, gradlew.bat, gradle/wrapper/   # Gradle 8.10 wrapper
├── runelite-plugin.properties
├── icon.png                        # ≤ 48x72
├── LICENSE                         # BSD 2-Clause
├── src/main/java/com/agilitycctracker/...
├── src/main/resources/com/agilitycctracker/...   # panel icon etc.
├── src/test/java/com/agilitycctracker/...        # unit tests + launcher
├── server/
│   ├── Dockerfile
│   ├── docker-compose.yml          # prod stack (api + mysql)
│   ├── docker-compose.test.yml     # throwaway mysql + pytest runner
│   ├── .env.example
│   ├── pyproject.toml              # deps + ruff + pytest config
│   ├── alembic.ini, alembic/versions/...
│   ├── app/
│   │   ├── main.py, config.py, db.py, security.py
│   │   ├── models/ (SQLAlchemy)
│   │   ├── schemas/ (Pydantic)
│   │   ├── api/v1/ (status.py, ingest.py)
│   │   ├── services/ (roster.py, dd.py, loadouts.py, hiscores.py, items.py)
│   │   ├── workers/ (hiscores_worker.py, items_worker.py)
│   │   └── web/ (routes/, templates/, static/)
│   └── tests/
├── deploy/
│   ├── backup.sh                   # mysqldump rotation
│   └── proxy/                      # proxy snippet(s) matching what Phase 0 finds
├── docs/
│   ├── BUILD_PLAN.md (this file), BUILD_LOG.md
│   ├── DEV_SETUP.md, API.md, SELF_HOSTING.md
│   ├── PLUGIN_HUB_SUBMISSION.md, FUTURE_NON_HUB.md
├── .github/workflows/ci.yml
└── CLAUDE.md
```

---

## 3. Build phases

### Phase 0 — Server discovery (read-only) → 🛑

Do **not** change anything in this phase. Collect and report:

1. `uname -a`, `/etc/os-release`, CPU/RAM (`free -h`), disk (`df -h`).
2. `docker version`, `docker compose version` (Compose v2 required). If Docker
   is missing → 🛑 ask.
3. `docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'`,
   `docker network ls`, `docker volume ls`. Identify gimhub-app and its MySQL.
4. Listening ports: `ss -tlnp` (or `netstat -tlnp`). Pick a free localhost port
   for the API (prefer 8090).
5. Reverse proxy: detect nginx (`nginx -T` if permitted, `/etc/nginx/sites-*`),
   Caddy (`/etc/caddy/Caddyfile`, container), Traefik (container labels), Apache,
   or none. Note how TLS is issued (certbot, Caddy auto-TLS, Traefik ACME).
6. Firewall: `ufw status` / `iptables -S` (read-only).
7. Whether `git`, `curl`, `cron` exist. Whether the repo remote is reachable
   (`git remote -v`, `git fetch --dry-run`).

Then 🛑 **report findings and ask the owner:**

- Public hostname for the tracker (e.g. `tracker.example.com`) and whether DNS
  already points to this server.
- Deploy directory (default `/opt/agilitycctracker`; the repo clone can live
  there directly).
- Git branch to commit to, and whether to push to the remote.
- Dashboard admin username (password will be generated).
- FC details: FC owner's RSN (the friends chat is identified by its owner name)
  and a display name for the group.
- Confirm the chosen API port and reverse-proxy approach.

Record answers in `docs/BUILD_LOG.md`.

### Phase 1 — Scaffold

1. **Plugin template** — recreate `runelite/example-plugin` at the repo root
   (fetch from `https://raw.githubusercontent.com/runelite/example-plugin/master/<file>`
   or write from §4.1):
   - `build.gradle` (§4.1, with `pluginMainClass = 'com.agilitycctracker.AgilityCCTrackerPluginTest'`
     and `group = 'com.agilitycctracker'`).
   - `settings.gradle`: `rootProject.name = 'agility-cc-tracker'`.
   - Gradle wrapper 8.10: easiest is
     `docker run --rm -u $(id -u):$(id -g) -v "$PWD":/p -w /p gradle:8.10-jdk11 gradle wrapper --gradle-version 8.10`.
   - `runelite-plugin.properties` (§4.2), `LICENSE` (BSD-2-Clause, owner as
     copyright holder — ask name if unknown, else "AgilityCCTracker contributors"),
     placeholder `icon.png` (48x72 or smaller; a simple generated PNG is fine).
   - Minimal `AgilityCCTrackerPlugin`, `AgilityCCTrackerConfig`,
     `AgilityCCTrackerPluginTest` launcher (§4.3).
2. **Server skeleton** — `server/pyproject.toml`, `app/main.py` with
   `GET /healthz` → `{"ok": true}`, `Dockerfile`, `docker-compose.yml`,
   `docker-compose.test.yml`, `.env.example`.
3. `.gitignore` covering Java/Gradle, Python, `.env`, `server/backups/`, IDE
   files.
4. `.github/workflows/ci.yml`: job 1 `actions/setup-java@v4` (temurin 11) +
   `./gradlew build`; job 2 MySQL 8.4 service + Python 3.12 + `ruff check` +
   `pytest`.

**Checks:**
```
docker run --rm -u $(id -u):$(id -g) -v "$PWD":/p -w /p -e GRADLE_USER_HOME=/p/.gradle gradle:8.10-jdk11 ./gradlew build --no-daemon
cd server && docker compose -f docker-compose.test.yml run --rm tests
```
(Add `.gradle/` to `.gitignore`.) Commit: `Phase 1: scaffold plugin and server`.

### Phase 2 — Plugin core (§5.1–5.6)

Config, `DataState`, `SelfStateCollector`, `LapTracker`, `DeathTracker`,
`RosterTracker`, `ApiClient`, `Uploader`, DTOs. Unit tests per §5.10.
**Checks:** gradle build + tests green. Commit.

### Phase 3 — Plugin loadouts, scanner, DD, panel (§5.7–5.9)

`Loadout`, `LoadoutEvaluator`, `ItemNameResolver`, `SelfLoadoutCheck`,
`FcMemberScanner`, `DdCallDetector`, `DdTracker`, `TrackerPanel`. Unit tests incl.
payload-shape test. **Checks:** build + tests green; grep checks from §5.11.
Commit.

### Phase 4 — Server API (§6.1–6.4)

Models, Alembic initial migration, seed data, token auth, rate limiting,
`GET /api/v1/status`, `POST /api/v1/ingest` with roster diffing, loadout checks,
DD merging, event assignment. pytest per §6.7.
**Checks:** `docker compose -f docker-compose.test.yml run --rm tests` green,
`ruff check` clean. Commit.

### Phase 5 — Dashboard + workers (§6.5–6.6)

Login/roles/CSRF, all pages, hiscores + items workers, CSV export. Tests for role
guards and key queries. **Checks:** as Phase 4 + manual smoke via
`docker compose up` locally bound. Commit.

### Phase 6 — Deploy on this server → 🛑 before public exposure

1. Generate `server/.env` from `.env.example` with strong random values
   (`openssl rand -hex 32` for `APP_SECRET`, DB passwords). `chmod 600`.
2. `docker compose -p agilitycctracker up -d --build` (from `server/`). API
   bound to `127.0.0.1:${API_PORT}`. Services `restart: unless-stopped`.
3. Container entrypoint runs `alembic upgrade head` then uvicorn.
4. Bootstrap admin: `docker compose exec api python -m app.cli create-admin <username>`
   → prints a generated password once. Give it to the owner.
5. Verify locally: `curl -s http://127.0.0.1:${API_PORT}/healthz`.
6. 🛑 Show the owner the proposed reverse-proxy change (file path + snippet from
   `deploy/proxy/`) and ask to apply. After approval: apply, reload proxy
   (validate config first, e.g. `nginx -t`), obtain TLS the way the server
   already does.
7. Backups: `deploy/backup.sh` (mysqldump via `docker compose exec -T mysql`,
   gzip, keep 14 days) → 🛑 ask before adding the cron entry (nightly 04:00).
8. **Checks:** `curl -s https://<host>/healthz`; dashboard login works over
   HTTPS; `docker ps` shows gimhub containers unchanged (compare with Phase 0).
Commit (without `.env`).

### Phase 7 — Hand back to the owner

1. Via CLI or dashboard: create the FC group (owner RSN from Phase 0), generate
   the plugin token (shown once), seed default loadouts (§7) and DD settings
   (tiles left empty until the owner captures them in game).
2. Print for the owner: `apiUrl` = `https://<host>`, the token, dashboard URL,
   admin credentials.
3. Write/finish docs: `DEV_SETUP.md` (§8), `SELF_HOSTING.md` (update:
   `git pull && docker compose -p agilitycctracker up -d --build`; backups;
   restore), `API.md`, `PLUGIN_HUB_SUBMISSION.md` (§9), `FUTURE_NON_HUB.md` (§10).
4. Final report: what was built, test results, anything skipped, next steps
   (owner does in-game testing on their PC per `DEV_SETUP.md`).
Commit + push.

### Phase 8 — Plugin Hub submission (later, with the owner)

Only after in-game testing and after the owner re-confirms the DD observer with
the RuneLite devs. See §9.

---

## 4. Plugin scaffolding reference

### 4.1 `build.gradle` (from runelite/example-plugin, verbatim except the two marked lines)

```groovy
plugins {
	id 'java'
}

repositories {
	mavenLocal()
	maven {
		url = 'https://repo.runelite.net'
		content {
			includeGroupByRegex("net\\.runelite.*")
		}
	}
	mavenCentral()
}

def runeLiteVersion = 'latest.release'
def pluginMainClass = 'com.agilitycctracker.AgilityCCTrackerPluginTest'   // changed

dependencies {
	compileOnly group: 'net.runelite', name:'client', version: runeLiteVersion

	compileOnly 'org.projectlombok:lombok:1.18.30'
	annotationProcessor 'org.projectlombok:lombok:1.18.30'

	testImplementation 'junit:junit:4.12'
	testImplementation group: 'net.runelite', name:'client', version: runeLiteVersion
	testImplementation group: 'net.runelite', name:'jshell', version: runeLiteVersion
}

group = 'com.agilitycctracker'   // changed

tasks.withType(JavaCompile).configureEach {
	options.encoding = 'UTF-8'
	options.release.set(11)
}

tasks.register('run', JavaExec) {
	classpath = sourceSets.test.runtimeClasspath
	mainClass = pluginMainClass

	jvmArgs "-ea"
	args "--developer-mode", "--debug"
}

tasks.register('shadowJar', Jar) {
	dependsOn configurations.testRuntimeClasspath
	manifest {
		attributes('Main-Class': pluginMainClass, 'Multi-Release': true)
	}

	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	from sourceSets.main.output
	from sourceSets.test.output
	from {
		configurations.testRuntimeClasspath.collect { file ->
			file.isDirectory() ? file : zipTree(file)
		}
	}

	exclude 'META-INF/INDEX.LIST'
	exclude 'META-INF/*.SF'
	exclude 'META-INF/*.DSA'
	exclude 'META-INF/*.RSA'
	exclude '**/module-info.class'

	group = BasePlugin.BUILD_GROUP
	archiveClassifier.set('shadow')
	archiveFileName.set("${rootProject.name}-${project.version}-all.jar")
}
```

If JUnit 4 tests need mocking, **do not add Mockito** unless it's already a
transitive dependency of the client; prefer designing pure-logic classes that are
testable without mocks (the plan below is structured for that).

### 4.2 `runelite-plugin.properties`

```
displayName=Wildy Agility FC Tracker
author=<owner name>
description=Tracks attendance, laps, loadout compliance and DD calls for a Wilderness Agility friends chat, syncing to a self-hosted server.
tags=agility,wilderness,friends chat,fc,mass,tracker,dd
plugins=com.agilitycctracker.AgilityCCTrackerPlugin
build=standard
```

### 4.3 Test launcher (`src/test/java/com/agilitycctracker/AgilityCCTrackerPluginTest.java`)

```java
package com.agilitycctracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class AgilityCCTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AgilityCCTrackerPlugin.class);
		RuneLite.main(args);
	}
}
```

This launches a full client — **only on a machine with a display** (the owner's
PC). On the server, only `./gradlew build` / `test` are run.

---

## 5. Plugin specification (`com.agilitycctracker`)

Architecture (borrowed from the Group Ironmen Tracker / GIM Hub plugins):
collect on the client thread via `@Subscribe` + `@Schedule`; keep latest state
per field and only send changed fields; upload from an asynchronous
`@Schedule` job using the injected `OkHttpClient` synchronously (`execute()`),
since that job is already off the client thread.

Use current (non-deprecated) RuneLite APIs:

| Use | Not (deprecated) |
|---|---|
| `client.getTopLevelWorldView().players()` (may be null-check `getTopLevelWorldView()`) | `client.getPlayers()` |
| `client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)` (=94), `.INV` (=93) | `net.runelite.api.InventoryID` enum |
| `client.getVarbitValue(net.runelite.api.gameval.VarbitID.INSIDE_WILDERNESS)` (5963) | `Varbits.IN_WILDERNESS` |
| `client.getWidget(net.runelite.api.gameval.InterfaceID.PvpIcons.WILDERNESSLEVEL)` | `ComponentID` / `WidgetInfo` |
| `net.runelite.api.gameval.ItemID` / `ObjectID` | old `net.runelite.api.ItemID` |
| `PlayerComposition.getGender()` | `isFemale()` |
| `client.getAccountHash()` | `client.getUsername()` |

If any of these names fail to compile against `latest.release`, check the
current source at
`https://raw.githubusercontent.com/runelite/runelite/master/runelite-api/src/main/java/net/runelite/api/...`
and adapt; note deviations in `BUILD_LOG.md`.

### 5.1 `AgilityCCTrackerConfig` — `@ConfigGroup("agilitycctracker")`

Sections: *Connection*, *Sharing*, *FC observer*, *Display*.

| keyName | type | default | notes |
|---|---|---|---|
| `apiUrl` | String | `""` | Base URL, e.g. `https://tracker.example.com`. Plugin idle when blank. |
| `apiToken` | String | `""` | `secret = true`. FC group token from dashboard. |
| `shareStats` | boolean | true | Own skills/combat level. |
| `shareGear` | boolean | true | Own worn items + gear/risk value. |
| `shareRoster` | boolean | true | FC roster during active events. |
| `fcObserver` | boolean | true | Report loadout gaps / DD presence of FC members in view. Effective only if server `observer_enabled` is true. |
| `showPanel` | boolean | true | Sidebar panel. |
| `loadoutWarnings` | boolean | true | Chat warning when own loadout has gaps. |

### 5.2 `AgilityCCTrackerPlugin`

- `@PluginDescriptor(name = "Wildy Agility FC Tracker", description = ..., tags = {...})`.
- Injects: `Client`, `ClientThread`, `ConfigManager`, config, `ItemManager`,
  `ClientToolbar`, `OkHttpClient`, `Gson`, plus the components below (Guice
  `@Singleton` classes).
- `@Provides AgilityCCTrackerConfig provideConfig(ConfigManager cm)`.
- Skip all collection/upload on unsupported worlds: if
  `client.getWorldType()` contains any of `SEASONAL`, `DEADMAN`, `BETA_WORLD`,
  `TOURNAMENT_WORLD`, `PVP_ARENA`, `QUEST_SPEEDRUNNING`, `NOSAVE_MODE`,
  `FRESH_START_WORLD` (check which constants exist in the current `WorldType`).
- Reset all state on `GameStateChanged` to `LOGIN_SCREEN` / `HOPPING` as
  appropriate, and when `accountHash` changes.
- Routes events to components; components contain the logic.

### 5.3 `state/DataState<T>`

GIM-style holder:
```java
class DataState<T> {
  private final AtomicReference<T> pending = new AtomicReference<>();
  private T lastSent;                       // guarded by this
  synchronized void update(T v)  { if (!Objects.equals(v, lastSent)) pending.set(v); }
  T consume()                    { return pending.getAndSet(null); }
  synchronized void markSent(T v){ lastSent = v; }
  void restore(T v)              { pending.compareAndSet(null, v); }
}
```
Values must be immutable value objects with proper `equals` (Lombok `@Value`).

### 5.4 Collectors

**`collect/SelfStateCollector`**
- `onStatChanged`: build `Map<String, Integer> xpBySkill` from
  `client.getSkillExperience(skill)` for all `Skill.values()` (skip `OVERALL` if
  present), and `combatLevel = client.getLocalPlayer().getCombatLevel()`.
- `onItemContainerChanged` for `InventoryID.WORN`: 14 slots → list of
  `{slot, id, qty}` (id −1 = empty). Gear value = Σ
  `itemManager.getItemPrice(id) * qty`. For `InventoryID.INV`: compute risk value
  (worn + inventory) and wildy-ticket count (`gameval.ItemID.WILDY_AGILITY_TOKEN`
  = 29460).
- 1-second `@Schedule(period = 1, unit = ChronoUnit.SECONDS)` on the client
  thread: world (`client.getWorld()`), HP/prayer boosted/real, wildy level,
  `onCourse`, own skull (`client.getLocalPlayer().getSkullIcon()`, int constant,
  −1 = none).
- Wildy level: `getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1`, then parse the
  widget `InterfaceID.PvpIcons.WILDERNESSLEVEL` text with
  `Pattern.compile("^Level: (\\d+)$")` (this is what core `CombatLevelPlugin`
  does). Fallback when widget missing: `(y - 3520) / 8 + 1` for surface
  coordinates with y ≥ 3520.
- `onCourse`: local player's `WorldPoint.getRegionID() == 11837` (Wilderness
  Agility Course region; x 2944–3007, y 3904–3967).

**`collect/LapTracker`**
- `onChatMessage` with `ChatMessageType.GAMEMESSAGE`, matching the **raw**
  message (colour tags present) with:
  ```
  Your Wilderness Agility lap count is: (?:<col=[0-9a-f]{6}>)?([0-9,]+)(?:</col>)?\.?
  ```
  (The core ChatCommands `KILLCOUNT_PATTERN` also matches this message with
  boss = "Wilderness Agility".)
- Lap end tiles (from core `plugins/agility/Courses.java`,
  `WILDERNESS(571.4, 11837, (2993,3933,0), (2994,3933,0), (2995,3933,0))`) — use
  as a secondary signal / for lap timing if the chat message is disabled.
- On each lap: `{lap_count, completed_at (epoch ms), lap_time_ms (since previous
  lap end, null for first), tickets_gained (ticket count delta since previous
  lap)}` → queue.

**`collect/DeathTracker`**
- `onActorDeath` where `actor == client.getLocalPlayer()` and in wilderness →
  `{died_at, world, wildy_level}`. **No killer / attacker information.**

**`collect/RosterTracker`**
- Active only when `client.getFriendsChatManager() != null`, its `getOwner()`
  equals (case-insensitive, `Text.toJagexName`) the `fc_owner` from `/status`,
  an event is active, and `shareRoster` is on.
- On `FriendsChatChanged(joined=true)`, and debounced 5 s after any
  `FriendsChatMemberJoined` / `FriendsChatMemberLeft`: snapshot
  `getMembers()` → `[{name: Text.toJagexName(m.getName()), rank: m.getRank().getValue(), world: m.getWorld()}]`.
  Also resend a full snapshot every 60 s during an event (lets the server close
  stale sessions).
- Normalise names with `net.runelite.client.util.Text.toJagexName` (FC names can
  contain non-breaking spaces).

### 5.5 `api/ApiClient`

- Injected `OkHttpClient` and `Gson`. Base URL from config, trailing slash
  trimmed; require `https://` unless host is `localhost`/`127.0.0.1`.
- Headers: `Authorization: Bearer <token>`,
  `User-Agent: AgilityCCTracker/<pluginVersion> RuneLite/<RuneLiteProperties.getVersion()>`,
  `Accept: application/json`, `Content-Type: application/json`.
- `getStatus()` → `StatusResponse`; `postIngest(IngestBatch)`.
- Error handling: network error / 5xx → skip next 10 attempts (backoff);
  401/403 → stop uploading and show "invalid token" in panel until `apiToken`
  or `apiUrl` changes (`ConfigChanged`); 429 → back off 60 s.

### 5.6 `api/Uploader`

- `@Schedule(period = 2, unit = SECONDS, asynchronous = true)`: build one
  `IngestBatch`:
  ```json
  {
    "rsn": "Player Name",
    "account_hash": "<sha256 hex of client.getAccountHash(), optional>",
    "self": { "world": 330, "combat_level": 98, "skills": {"attack": 13034431, ...},
              "equipment": [{"slot": 3, "id": 861, "qty": 1}, ...],
              "gear_value": 123456, "risk_value": 234567, "wildy_level": 52,
              "on_course": true, "skulled": false, "loadout_failed": ["run-helm"] },
    "laps":   [{"lap_count": 1234, "completed_at": 1727190000000, "lap_time_ms": 61200, "tickets_gained": 1}],
    "deaths": [{"died_at": 1727190000000, "world": 330, "wildy_level": 52}],
    "roster": {"ts": 1727190000000, "members": [{"name": "A", "rank": 0, "world": 330}]},
    "loadout_checks": [{"rsn": "B", "loadout_id": 1, "failed": ["run-bow"], "ts": 1727190000000}],
    "dd": { "called_at": 1727190000000, "broken_at": null,
            "presence": [{"rsn": "B", "arrived_after_ms": 4200, "ms_on_tile": 42000,
                          "left_before_break": false, "seen_running": false,
                          "observed_ms": 60000, "dd_failed": []}] }
  }
  ```
  Omit empty/unchanged sections; skip the POST if nothing but `rsn` remains.
  `self` includes only fields allowed by the sharing toggles.
- Snapshot the RSN / game values on the client thread (`clientThread.invoke`)
  before the async send, or keep them in thread-safe holders updated by event
  handlers. Never call `client.*` from the async thread.
- On failure: `restore()` the consumed `DataState`s and re-queue laps/deaths/checks
  (bounded queues, e.g. max 500 items each).
- `@Schedule(period = 60, unit = SECONDS, asynchronous = true)`: `GET /status`,
  store `StatusResponse` in an `AtomicReference` used by all components. Also
  poll immediately on startup and on `ConfigChanged`.

`StatusResponse`:
```json
{ "fc_owner": "Owner Name", "fc_name": "Wildy Agility",
  "server_time": 1727190000000,
  "observer_enabled": true,
  "active_event": {"id": 12, "name": "Tuesday mass", "started_at": 1727190000000} ,
  "loadouts": [ { "id": 1, "name": "Running", "context": "RUNNING",
                  "requirements": [ {"id": "run-weapon", "label": "No weapon in hand",
                                     "slot": "WEAPON", "check": "FILLED", "args": [], "when": null}, ... ] } ],
  "dd": { "tiles": [{"x": 0, "y": 0, "plane": 0}, {"x": 0, "y": 0, "plane": 0}],
          "call_regex": "^\\s*dd\\s*!*$", "break_regex": "^\\s*(break|brk)\\s*(dd)?\\s*!*$",
          "min_rank": 3, "arrive_grace_s": 15, "leave_grace_s": 5, "max_minutes": 10,
          "crossbow_counts_as_bow_at_dd": true } }
```
(`active_event` is `null` when none. Regexes are matched case-insensitively.)

### 5.7 Loadouts

**`loadout/Slot`** enum: `HEAD, CAPE, AMULET, WEAPON, TORSO, SHIELD, LEGS, HANDS, BOOTS, RING, AMMO`
with mapping to `EquipmentInventorySlot` (own container) and to `KitType`
(others; `RING`/`AMMO` have no `KitType` → **not visible on other players**).

**`loadout/Requirement`**: `{String id, String label, Slot slot, Check check, List<String> args, Condition when}`
where `Check ∈ {FILLED, EMPTY, NAME_ENDS_WITH, NAME_NOT_ENDS_WITH, ITEM_IN, ITEM_NOT_IN}`
(`args` are name suffixes or item ids as strings) and `Condition` is
`{Slot slot, Check check, List<String> args}` or null.

**`loadout/LoadoutEvaluator`** (pure, no client imports):
```java
/** Returns ONLY the ids of failed requirements. Never returns item data. */
List<String> evaluate(Loadout loadout, Map<Slot, WornItem> worn, Set<Slot> visibleSlots)
```
- `WornItem{int id; String normalizedName}`; missing key = empty slot.
- Requirements whose `slot` (or `when.slot`) is not in `visibleSlots` are skipped
  (for other players `visibleSlots` excludes RING and AMMO).
- If `when` is set and not satisfied, the requirement is skipped (passes).
- `NAME_ENDS_WITH`: normalised name ends with any arg (args also normalised).
  An empty slot fails `NAME_ENDS_WITH` and passes `NAME_NOT_ENDS_WITH`.

**`loadout/ItemNameResolver`**: client-thread only; cache `id → normalizedName`
using `itemManager.getItemComposition(itemManager.canonicalize(id)).getName()`.
Normalise: lower-case, trim, strip trailing bracketed tags repeatedly
(`\s*\([^)]*\)\s*$` → e.g. "Magic shortbow (i)" → "magic shortbow",
"Dragon crossbow (cr)" → "dragon crossbow").

**`loadout/SelfLoadoutCheck`**: on WORN change and on status refresh, evaluate
the `RUNNING` loadout on all 11 slots of the local player. On change, push
`loadout_failed` into self state, and if `loadoutWarnings` send a game chat
message (`client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>FC loadout: missing boots, missing necklace</col>", null)`),
at most once per change.

### 5.8 `scan/FcMemberScanner` — the only class that reads other players

Runs on `GameTick` when **all** hold: `fcObserver` on, `status.observer_enabled`,
an event is active, the local player is in region 11837 (or wilderness), and the
FC owner matches.

For each `Player p` in `client.getTopLevelWorldView().players()`:
1. Skip `p == localPlayer`, `p.getName() == null`, `!p.isFriendsChatMember()`,
   or name not in current roster (normalised).
2. Build an **ephemeral** observation:
   - `onDdTile` = `p.getWorldLocation()` equals one of the DD tiles.
   - `inCourseArea` = region 11837.
   - Visible worn items from `p.getPlayerComposition().getEquipmentIds()` indexed
     by `KitType` ordinal (HEAD, CAPE, AMULET, WEAPON, TORSO, SHIELD, ARMS, LEGS,
     HAIR, HANDS, BOOTS, JAW): values `>= PlayerComposition.ITEM_OFFSET` (2048)
     are items → `id - 2048`; values in [256, 2048) are kits (i.e. body shows,
     slot empty); `< 256` / −1 = empty. Only map HEAD, CAPE, AMULET, WEAPON,
     TORSO, SHIELD, LEGS, HANDS, BOOTS. Resolve names via `ItemNameResolver`.
3. `failed = evaluator.evaluate(runningLoadout, worn, VISIBLE_SLOTS)`; if
   different from the last result for that RSN this event → queue
   `LoadoutCheck{rsn, loadoutId, failed, ts}` (an empty list = compliant; send
   once).
4. Pass `(rsn, onDdTile, inCourseArea, ddFailed)` to `DdTracker` where
   `ddFailed = onDdTile ? evaluator.evaluate(ddLoadout, worn, VISIBLE_SLOTS) : List.of()`.
5. Discard the observation. Do not store coordinates or item ids in fields.

Throttle to at most one full scan per tick; cap at 200 players.

### 5.9 DD

**`dd/DdCallDetector`**
- `onChatMessage` with type `FRIENDSCHAT`; FC owner must match status.
- Sender: `Text.toJagexName(Text.removeTags(chatMessage.getName()))`; look up
  `friendsChatManager.findByName(sender)`; require
  `member.getRank().getValue() >= status.dd.min_rank`
  (`FriendsChatRank`: UNRANKED −1, FRIEND 0, RECRUIT 1, CORPORAL 2, SERGEANT 3,
  LIEUTENANT 4, CAPTAIN 5, GENERAL 6, OWNER 7, JMOD 127).
- Message text: `Text.removeTags(chatMessage.getMessage())`, match
  `call_regex` / `break_regex` (case-insensitive).
- Call → open window `{called_at = now}` (ignore if already open). Break → close
  `{broken_at = now}`. Auto-close after `max_minutes`.
- Also available when an event is active only.

**`dd/DdTracker`** (pure logic fed by the plugin each tick; unit-testable with a
fake clock)
- While a window is open, each tick receives samples for the local player
  (own position + own worn items → DD loadout on all slots) and for observed FC
  members.
- Per RSN keep: `firstOnTileAt`, `msOnTile` (tick-accumulated, 600 ms/tick),
  `lastOnTileAt`, `leftBeforeBreak`, `seenRunning`, `observedMs`,
  `ddFailed` (union while on tile).
  - `seenRunning` = observed in course area, **not** on a DD tile, at a time
    later than `called_at + arrive_grace_s`.
  - `leftBeforeBreak` = was on tile, then off tile continuously for more than
    `leave_grace_s` while the window is still open.
- Every 10 s and at close, emit a `dd` section with all per-RSN summaries
  (cumulative, idempotent — server keeps the max / OR / union).
- Laps completed during the window are derived server-side from lap timestamps.

**`ui/TrackerPanel`** (`PluginPanel`, added via `ClientToolbar` +
`NavigationButton` with an icon from resources)
- Connection status (OK / no URL / invalid token / backing off), FC + active
  event, own laps & tickets this event, own loadout status, DD status (active?
  elapsed, own time on tile).
- For observers: list of FC members in view with gaps (labels only).
- Button **"Copy my tile"** → copies `x,y,plane` of the local player to the
  clipboard (for entering DD tiles on the dashboard).

### 5.10 Plugin unit tests (JUnit 4)

- Lap regex: coloured and plain messages, commas in numbers.
- Wildy-level widget text parsing + coordinate fallback.
- Name normaliser: "Magic shortbow (i)", "Rune crossbow", "Dragon crossbow (cr)",
  "Toxic blowpipe", "Bow of faerdhinen (c)".
- Evaluator with default loadouts (§7): shortbow no shield → pass; rune crossbow
  no shield → fails `run-xbow-shield`; crossbow + shield → pass; blowpipe →
  fails `run-bow`; empty hand → fails `run-weapon` and `run-bow`; missing helm /
  legs / boots / necklace; RING/AMMO requirements skipped when not visible.
- `DdCallDetector`: phrases ("DD", "dd!!", "break dd", "brk", "BREAK DD"),
  rank gate, wrong FC ignored.
- `DdTracker` scripted timelines: on time; late (arrived_after_ms); left early;
  kept running; wrong weapon at DD; window auto-close.
- `DataState` update/consume/restore semantics.
- Roster snapshot debounce/diff.
- **Payload-shape test**: serialise `LoadoutCheck` and `DdPresence` with Gson and
  assert no forbidden keys (§1.2).

### 5.11 Hub-readiness grep checks (must pass at end of Phase 3)

```
grep -rn "getPlayers()\|players()\|getPlayerComposition" src/main/java | grep -v scan/FcMemberScanner   # expect only local-player uses, if any
grep -rn "PlayerSpawned\|PlayerDespawned\|InteractingChanged\|HitsplatApplied\|AnimationChanged\|PlayerChanged" src/main/java   # expect none
grep -rn "java.lang.reflect\|Runtime.getRuntime\|ProcessBuilder\|new OkHttpClient\|new Gson" src/main/java   # expect none
```

---

## 6. Server specification (`server/`)

### 6.1 Stack & config

- Python 3.12, FastAPI, Uvicorn, SQLAlchemy 2.x async + `asyncmy`, Alembic,
  Pydantic v2 + `pydantic-settings`, `httpx`, `argon2-cffi`, `itsdangerous`
  (sessions), Jinja2, `python-multipart`. Dev: pytest, pytest-asyncio, ruff.
- Frontend assets: HTMX, Pico.css, Chart.js — **vendor them into
  `app/web/static/vendor/`** (download once at build time and commit, or fetch in
  Dockerfile) so the dashboard doesn't depend on CDNs.
- Settings (env): `DB_HOST=mysql`, `DB_PORT=3306`, `DB_NAME=agilitycc`,
  `DB_USER=agilitycc`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `APP_SECRET`,
  `APP_URL`, `API_PORT=8090`, `PUBLIC_READ=false`, `LOG_LEVEL=info`,
  `HISCORES_ENABLED=true`, `ITEMS_ENABLED=true`, `USER_AGENT="AgilityCCTracker/1.0 (+<APP_URL>)"`.

`docker-compose.yml` (sketch):
```yaml
name: agilitycctracker
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: ${DB_NAME}
      MYSQL_USER: ${DB_USER}
      MYSQL_PASSWORD: ${DB_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    command: ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci"]
    volumes: [db_data:/var/lib/mysql]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      retries: 10
    restart: unless-stopped
    networks: [internal]
  api:
    build: .
    env_file: .env
    depends_on: { mysql: { condition: service_healthy } }
    ports: ["127.0.0.1:${API_PORT}:8000"]
    healthcheck:
      test: ["CMD", "python", "-c", "import urllib.request;urllib.request.urlopen('http://localhost:8000/healthz')"]
      interval: 30s
    restart: unless-stopped
    networks: [internal]
volumes: { db_data: {} }
networks: { internal: {} }
```
(No MySQL port is published to the host.) Uvicorn runs with
`--proxy-headers --forwarded-allow-ips=*` behind the reverse proxy (restrict to
the proxy's address if known) so client IPs are right for rate limiting.

### 6.2 Schema (Alembic migration `0001_initial`)

MySQL 8.4, InnoDB, `utf8mb4`, collation `utf8mb4_0900_ai_ci` (case-insensitive,
so RSN uniqueness is case-insensitive). Timestamps `DATETIME(3)` in UTC.
JSON columns where Postgres arrays would be used.

```
fc_groups(id PK, name, owner_rsn, token_hash CHAR(64) UNIQUE, created_at,
          observer_enabled BOOL DEFAULT TRUE,
          dd_tiles JSON, dd_call_regex, dd_break_regex, dd_min_rank INT DEFAULT 3,
          dd_arrive_grace_s INT DEFAULT 15, dd_leave_grace_s INT DEFAULT 5,
          dd_max_minutes INT DEFAULT 10, dd_crossbow_counts_as_bow BOOL DEFAULT TRUE)
players(id PK, rsn VARCHAR(12) UNIQUE, first_seen, last_seen,
        is_plugin_user BOOL, latest_snapshot_id NULL,
        hiscores_updated_at NULL)
player_snapshots(id PK, player_id FK, captured_at, source ENUM('plugin','hiscores'),
        world, combat_level, skills JSON, equipment JSON NULL, gear_value BIGINT NULL,
        risk_value BIGINT NULL)          -- equipment only ever from the player's own plugin
events(id PK, fc_group_id FK, name, started_at, ended_at NULL, started_by_user_id, notes)
attendance(id PK, event_id FK, player_id FK, joined_at, left_at NULL, rank INT, world INT,
        INDEX(event_id, player_id))
laps(id PK, player_id FK, event_id FK NULL, lap_count INT, completed_at,
        lap_time_ms INT NULL, tickets_gained INT NULL, UNIQUE(player_id, lap_count))
deaths(id PK, player_id FK, event_id FK NULL, died_at, world, wildy_level,
        UNIQUE(player_id, died_at))
loadouts(id PK, fc_group_id FK, name, context ENUM('running','dd'), enabled BOOL)
loadout_requirements(id PK, loadout_id FK, req_key VARCHAR(64), label, slot,
        check_type, args JSON, when_json JSON NULL, sort INT, UNIQUE(loadout_id, req_key))
loadout_checks(id PK, player_id FK, event_id FK, loadout_id FK, checked_at,
        failed_requirement_keys JSON, source ENUM('self','fc_scan','staff_manual'),
        reported_by_player_id NULL, reported_by_user_id NULL, notes NULL)
dd_calls(id PK, event_id FK, called_at, broken_at NULL, reports INT, edited_by_user_id NULL)
dd_presence(id PK, dd_call_id FK, player_id FK, arrived_after_ms INT NULL,
        ms_on_tile INT, left_before_break BOOL, seen_running BOOL,
        laps_during_dd INT DEFAULT 0, observed_ms INT, dd_loadout_failed JSON,
        sources JSON, UNIQUE(dd_call_id, player_id))
raggers(id PK, rsn UNIQUE, status ENUM('active','cleared'), threat_notes TEXT,
        added_by_user_id, created_at, combat_level NULL, skills JSON NULL,
        hiscores_updated_at NULL)
ragger_sightings(id PK, ragger_id FK, event_id FK NULL, seen_at, world NULL,
        gear_notes TEXT NULL, reported_by_user_id)
users(id PK, username UNIQUE, password_hash, role ENUM('admin','staff','viewer'),
        created_at, last_login_at NULL)
items(id PK (item id), name, ge_price INT NULL, ha_value INT NULL, updated_at)
```

### 6.3 Auth

- **Plugin:** `Authorization: Bearer <token>`. Tokens are 32 random bytes
  (url-safe). Stored as `HMAC-SHA256(APP_SECRET, token)` hex in
  `fc_groups.token_hash`; lookup by hash; compare with `hmac.compare_digest`.
- Failed-auth rate limit: 5 failures per IP per 5 minutes → 429 (in-memory
  sliding window; single process is fine).
- Request body limit ~100 KB (reject larger with 413). Validate list sizes
  (roster ≤ 600, laps ≤ 200, checks ≤ 300, presence ≤ 300).
- **Dashboard:** username + argon2 password; signed session cookie
  (`HttpOnly`, `Secure` when `APP_URL` is https, `SameSite=Lax`); CSRF token on
  every POST form. Roles: `viewer` (read), `staff` (events, DD edits, loadouts,
  raggers, manual gaps), `admin` (users, FC groups, tokens, settings).
  `PUBLIC_READ=true` allows unauthenticated read-only pages (default false).
- CLI: `python -m app.cli create-admin <username>`, `create-group <name> <owner_rsn>`,
  `rotate-token <group_id>`, `seed-loadouts <group_id>`.

### 6.4 Plugin API (`/api/v1`)

- `GET /healthz` (no auth) → `{"ok": true}`.
- `GET /api/v1/status` → `StatusResponse` (§5.6) for the token's FC group.
- `POST /api/v1/ingest` → `IngestBatch` (§5.6). Processing in one transaction:
  1. Upsert reporter `players` row by `rsn`; set `is_plugin_user = true`,
     `last_seen`.
  2. `self`: if changed vs latest plugin snapshot → insert `player_snapshots`
     (`source='plugin'`). `self.loadout_failed` → `loadout_checks`
     (`source='self'`) if changed and an event is active.
  3. `laps`: insert ignore on `UNIQUE(player_id, lap_count)`; `event_id` =
     event whose window contains `completed_at`.
  4. `deaths`: insert ignore; assign event likewise.
  5. `roster` (only if event active and `shareRoster` data present): upsert
     players; diff against open attendance sessions for this event:
     new names → open session; missing names → close session
     (`left_at = roster.ts`). Multiple reporters are fine: a name disappears only
     if absent from a snapshot newer than its last-seen time **and** the
     reporter is on the same world as the session (avoid closing sessions based
     on a reporter who hopped). Keep this logic in `services/roster.py` with
     thorough tests.
  6. `loadout_checks` (only if event active, `observer_enabled`, and RSN has an
     attendance row in this event): store if different from the latest stored
     result for that player/event/loadout.
  7. `dd` (only if event active): find `dd_calls` row for this event with
     `|called_at - dd.called_at| <= 10 s` else create; `reports += 1`;
     `broken_at` = earliest non-null reported (unless staff-edited). For each
     presence (observer data only if `observer_enabled`; the reporter's own
     presence always): upsert `dd_presence` with `ms_on_tile = max`,
     `observed_ms = max`, `arrived_after_ms = min`, flags OR-ed,
     `dd_loadout_failed` union, `sources` add reporter.
  8. After a DD call closes (or on read), compute `laps_during_dd` from `laps`
     with `completed_at` in `[called_at, broken_at]`; any lap → also set
     `seen_running = true`.
- Responses: `{"ok": true, "server_time": ...}`; 400 on validation errors with
  details; never echo tokens.

### 6.5 Dashboard pages (Jinja2 + HTMX)

- **Login / logout.**
- **Events:** list; "Start event" (name, notes) and "Stop event" (staff); only
  one active event per FC group.
- **Event detail** (tabs):
  - *Attendance:* RSN, rank, total time present (sum of sessions), plugin user
    ✅/—, loadout status (✅ compliant / ❌ gap labels / "not checked"), laps.
  - *Laps:* leaderboard for the event; Chart.js line of laps over time.
  - *DD:* list of DD calls (called at, duration, # arrived / # attendees). Per
    call: table of attendees — came? (✅ + "+Ns" after call), time on tile,
    ⚠ left before break, ⚠ kept running / laps during DD, ⚠ not holding a bow,
    "not observed" when no data. Staff can edit call/break times.
  - *Deaths & raggers:* deaths list; ragger sightings logged for this event.
  - *Manual gap:* staff form to log a loadout gap for an attendee
    (`source='staff_manual'`).
- **DD leaderboard:** total `ms_on_tile` per player over an event or date range;
  "Top 3 DDers" card; CSV export (`/dd/leaderboard.csv?from=&to=`).
- **Loadouts:** list/edit loadouts and requirements (slot, check, args, when,
  label); item search (by name from `items`) to add item ids for `ITEM_IN` /
  `ITEM_NOT_IN`.
- **Players:** list (search); profile with latest stats (plugin snapshot, else
  hiscores), own gear (plugin users only, names + values from `items`, icons
  from `https://static.runelite.net/cache/item/icon/<id>.png`), lap history,
  events attended, loadout history, DD totals.
- **Raggers:** list/add/edit (RSN, status, notes); log sighting (event, time,
  world, gear notes typed by staff); hiscore stats + combat level; "refresh".
- **Settings (admin):** DD tiles (two `x,y,plane` inputs — paste from the plugin's
  "Copy my tile"), call/break regex, min rank, grace periods, max minutes,
  crossbow-counts-as-bow, `observer_enabled` kill switch.
- **Admin:** users (create, role, reset password), FC groups, token rotation
  (show once).

### 6.6 Workers (asyncio tasks in FastAPI lifespan)

- **Hiscores:** every minute, queue players (non-plugin attendees of recent
  events) and raggers with `hiscores_updated_at` older than 24 h. Fetch
  `https://secure.runescape.com/m=hiscore_oldschool/index_lite.json?player=<rsn>`
  with the configured User-Agent, ≤ 1 request / 2 s, handle 404 (not ranked /
  renamed) gracefully. Parse skills (level + xp); compute combat level:
  `base = 0.25*(def + hp + floor(prayer/2))`;
  `melee = 0.325*(att + str)`; `range = 0.325*floor(ranged*1.5)`;
  `mage = 0.325*floor(magic*1.5)`; `cb = floor(base + max(melee, range, mage))`.
  Store as `player_snapshots(source='hiscores', equipment NULL)` or on
  `raggers`.
- **Items:** daily, `GET https://prices.runescape.wiki/api/v1/osrs/mapping`
  (id, name, highalch) and `/latest` (use `high` as price) with the descriptive
  User-Agent → upsert `items`.

### 6.7 Server tests (pytest, against MySQL from `docker-compose.test.yml`)

Auth (valid/invalid token, rate limit), ingest idempotency (same batch twice),
event assignment by timestamp, roster diff (join/leave, multiple reporters, world
hop), loadout check change-only storage and attendance gate, observer kill
switch, DD call merge within ±10 s, DD presence merge (max/min/OR/union), laps
during DD, DD leaderboard query + CSV, hiscore parsing (fixture JSON) + combat
formula, dashboard role guards and CSRF.

---

## 7. Default loadouts (seed data)

Requirement keys are stable strings used by plugin and server.

**Running** (`context = running`) — checked on FC members running during an event:

| key | label | slot | check | args | when |
|---|---|---|---|---|---|
| `run-weapon` | No weapon in hand | WEAPON | FILLED | – | – |
| `run-bow` | Weapon isn't a bow | WEAPON | NAME_ENDS_WITH | `["bow"]` | – |
| `run-helm` | Missing helm | HEAD | FILLED | – | – |
| `run-legs` | Missing legs | LEGS | FILLED | – | – |
| `run-boots` | Missing boots | BOOTS | FILLED | – | – |
| `run-neck` | Missing necklace | AMULET | FILLED | – | – |
| `run-xbow-shield` | Crossbow without shield | SHIELD | FILLED | – | `{slot: WEAPON, check: NAME_ENDS_WITH, args: ["crossbow"]}` |

Rationale from FC leaders: bows are two-handed so a bow needs no shield; a
crossbow is one-handed so a shield is required. "Crossbow" also ends in "bow",
so crossbows pass `run-bow`.

**DD** (`context = dd`) — checked only on players standing on the DD tiles
during a DD call:

| key | label | slot | check | args |
|---|---|---|---|---|
| `dd-bow` | Not holding a bow at DD | WEAPON | NAME_ENDS_WITH | `["bow"]` |

If `dd_crossbow_counts_as_bow` is false, the server adds a second requirement
`dd-no-xbow` (WEAPON `NAME_NOT_ENDS_WITH ["crossbow"]`, label "Crossbow at DD")
when serving `/status`. Purpose: stop people freezing PKers at DD (magic weapons
/ staves fail the check).

Known name-rule edge cases to handle via `ITEM_IN` allowlists on the dashboard if
the FC wants them allowed: "Bow of faerdhinen" (doesn't end in "bow"),
"Toxic blowpipe", thrown weapons.

---

## 8. `docs/DEV_SETUP.md` — owner's PC setup (write this in Phase 7)

The owner does in-game testing on their own PC. No need to clone the RuneLite
repo — Gradle downloads the released client.

1. Install Git, JDK 11 (Eclipse Temurin 11), IntelliJ IDEA Community.
2. Clone this repo; open in IntelliJ as a Gradle project; project SDK = 11.
3. Run the Gradle `run` task or `AgilityCCTrackerPluginTest.main` → RuneLite opens
   in developer mode with "Wildy Agility FC Tracker" loaded.
4. Jagex account login (one-time): in the official RuneLite launcher →
   Configure → add `--insecure-write-credentials` to client arguments, log in
   once via the Jagex Launcher; this writes `~/.runelite/credentials.properties`
   which the dev client reuses. Legacy accounts log in directly. Delete the file
   when done.
5. In the plugin config set `apiUrl` = `https://<tracker host>` and the token.
6. Manual test checklist:
   - Join the FC, start an event on the dashboard → attendance appears.
   - Run a lap → lap row, tickets, snapshot on dashboard.
   - Unequip boots → chat warning + gap on dashboard.
   - Second account/friend in view with a crossbow and no shield → dashboard
     shows "Crossbow without shield"; server log/request body contains only
     requirement keys, no item ids.
   - Capture both DD tiles with "Copy my tile" → Settings. A ranked account types
     "DD" in FC chat; one account stands on a tile, the other keeps running;
     type "break dd" → DD tab shows arrival time, time on tile, "kept running";
     leaderboard updates.
   - Stop the event.
7. Beta testers without an IDE: `./gradlew shadowJar` →
   `java -jar build/libs/agility-cc-tracker-*-all.jar --developer-mode`
   (same credentials step).

---

## 9. `docs/PLUGIN_HUB_SUBMISSION.md` (write in Phase 7; execute in Phase 8)

1. Repo must be **public** (the Hub builds from it). Consider whether `server/`
   should stay in the same repo (fine) — no secrets are committed.
2. Ensure `runelite-plugin.properties`, `icon.png`, `LICENSE` (BSD-2) are present;
   `./gradlew build` passes on Java 11.
3. Fork `runelite/plugin-hub`, add `plugins/agility-cc-tracker`:
   ```
   repository=https://github.com/<owner>/<repo>.git
   commit=<full 40-char commit sha>
   warning=This plugin sends your RSN, stats, worn equipment, inventory value, wilderness agility laps and deaths, the names, ranks and worlds of members of the configured friends chat, whether nearby friends chat members meet the chat's required loadout and whether they are on the chat's DD tiles during a DD call, and your IP address to a server not controlled or verified by the RuneLite developers.
   ```
4. PR description: explain the gaps-only loadout design (reference the owner's
   conversation with RuneLite devs and the `rogue-chests` precedent), the single
   `FcMemberScanner` class, the payload-shape test, and the DD observer
   (booleans/durations only, server kill switch).
5. Watch the "RuneLite Plugin Hub Checks"; fix and push new `commit=` values to
   the same PR. Later updates: bump `commit=` in a new PR.

---

## 10. `docs/FUTURE_NON_HUB.md` (write in Phase 7; NOT implemented)

Document, without implementing:
- **Automated ragger detection** (sideload-only module): `PlayerSpawned` in region
  11837/wilderness where `!isFriendsChatMember()`; `getCombatLevel()`;
  visible gear via `PlayerComposition.getEquipmentIds()` (no ring/ammo);
  `getSkullIcon()`; `InteractingChanged` / `HitsplatApplied` / `ActorDeath` to
  attribute attacks on FC members. Why it can never go on the Hub
  (crowdsourcing rule), Jagex third-party client guideline risk, data retention
  and privacy concerns.
- **Uploading other players' actual worn items** — not allowed; only loadout gaps
  are.
- **How sideloading works:** only developer mode / a client run from source can
  load external jars; Jagex accounts need the credentials workaround; unofficial
  clients carry account risk.
- **Hub-safe manual alternative:** a staff hotkey/menu entry that copies a
  player's RSN to the clipboard for entering on the dashboard.

---

## 11. Definition of done

- Plugin: `./gradlew build` green (all unit tests), grep checks (§5.11) clean.
- Server: pytest green against MySQL, `ruff check` clean.
- Deployed on this server under compose project `agilitycctracker`, reachable at
  `https://<host>` via the existing reverse proxy, backups scheduled (if
  approved), gimhub containers untouched.
- FC group + token + default loadouts created; owner has URL, token, admin login.
- Docs written: `DEV_SETUP.md`, `SELF_HOSTING.md`, `API.md`,
  `PLUGIN_HUB_SUBMISSION.md`, `FUTURE_NON_HUB.md`, `BUILD_LOG.md`.
- Everything committed (no secrets) and pushed if agreed.
