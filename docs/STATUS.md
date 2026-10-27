# AgilityCCTracker — Session Status

**Read this first when resuming.** `docs/BUILD_LOG.md` has the full detailed
history (one entry per phase/fix, very long); this file is a short
current-state summary plus pointers, specifically so a new session (or one
that's just been compacted) can get oriented fast without reading the whole
log. Update this file's "Where things stand" / "Open items" sections at the
end of any session that changes them — treat it like a handoff note, not an
append-only log (that's what BUILD_LOG.md is for).

## Latest (2026-10-28) — dashboard accounts, kit item search

- Kit item search was missing untradeables (Ava's devices, fire cape,
  graceful...): the items worker used the Wiki's GE mapping. Now it loads
  RuneLite's full cache name dump (`static.runelite.net/cache/item/names.json`,
  ~21.7k items) plus `item/stats.ids.min.json` for each item's worn slot
  (`items.equip_slot`, migration `e5b0c8d1a624`). Search shows each name once
  (lowest id), best matches first, and slot boxes only offer items worn in
  that slot; saving a kit refuses wrong-slot gear (422).

- Self-registration: **Request one** on /login -> PENDING account (no
  access; login says it's awaiting approval). Admin → Users: approve with a
  role / reject, change roles, remove accounts (records they touched are kept,
  with the user link nulled), reset passwords (temporary, shown once).
  Everyone can change their own password on /account. Admin "create user"
  form removed. Owner account `grestinator` is `protected` (can't be
  removed, re-roled or password-reset from the dashboard); admins can't
  demote/remove themselves. Sign-ups limited to 3/hour/IP (in memory) and
  25 pending. Migration `c3f8a6b9e402`.
- An admin password reset sets `users.must_change_password` (migration
  `d4a9b7c0f513`): the temporary password only reaches /account until a new
  one (different from it) is chosen.

## Earlier (2026-10-27, night — pre-submission clean-up)

- **Data minimisation round 3.** No FC member list upload any more:
  attendance is opened by course sightings (now `{name, rank}`), including
  the player's own heartbeat while in/around the course (3x3 regions, so
  antis count). Ranks for gear-flag entitlement come with sightings. Plugin
  `RosterTracker`, "Share roster" (and the empty Sharing section),
  `SelfState.world`, death world/wildy level, `WildyLevelReader` and the DD
  `ddFailed` field are gone. Server: roster diff/service removed,
  `attendance.world`, `deaths.world/wildy_level`,
  `dd_presence.dd_loadout_failed` dropped, never-sighted attendance rows and
  orphan players deleted (migration `b2e7f5a8d391`). uvicorn
  `--no-access-log` (it logged IP + RSN every 10s) and json-file log
  rotation (10MB x 3) on both containers.
- **Fastest laps** (local): per-mass fastest + all-time best per account
  ("Fastest lap" / "Best ever" in This session; per-mass on history cards).

- **No stats collected.** Plugin no longer sends skills, combat level,
  wilderness level or skull status ("Share stats" option and the
  StatChanged listener removed); `SelfState` is now just `world` (roster
  diff) + `loadoutFailed`. The Agility ring reads xp locally. Server:
  Players pages, the OSRS hiscores worker (it looked up every non-plugin
  attendee's stats), `player_snapshots` and `players.latest_snapshot_id` /
  `hiscores_updated_at` removed (migration `a1d6e4f7c290`). Names in
  dashboard tables are plain text now.

- **The server never learns what anyone is wearing - the player included.**
  The plugin's own worn-item upload ("Share gear") is gone; only which
  required slots are empty is ever sent. Server ignores an `equipment` field
  from older builds; the unused `player_snapshots.equipment` column (20,576
  rows, never shown anywhere) is dropped. README, plugin description, config
  text and the Hub warning/PR draft reworded to "missing-gear checks".
- **Ragger tracking removed** (owner: not tracked now, don't set a
  precedent): model, routes, templates, nav, hiscores-worker branch, tests;
  tables `raggers` / `ragger_sightings` dropped (both were empty).
- Migration `f5c0d2e3b168` (after a backup). README rewritten; owner is
  sourcing the icon. Still `icon.png` placeholder until then.

## Earlier (2026-10-27, late — owner test notes, round 2)

- Kit-check and mass chat lines back to red (orange was unreadable).
- Footer host replaced by an "Agility CC Discord" link (discord.gg/agility);
  "Copy my tile" removed (DD tiles are set). Switch-role is now a pill button.
- "This mass" / "Previous sessions" tabs. History is **local only**
  (`history/SessionHistory`, RS-profile config, last 30 masses): laps,
  tickets, time in the course region, role(s). Kept off the server on
  purpose - the plugin token is public, so a server lookup would let anyone
  read anyone's history.
- Bank highlighting uses `ItemManager.getItemOutline` (traces the sprite).
- Dashboard attendance only lists people who turned up: new
  `attendance.sighted_at_course` (migration `e4b9c1d2a057`, backfilled from
  `last_sighted_at <> joined_at`, deployed after a backup), set by course
  sightings; plus anyone with a stint or a lap. Manual-gap dropdown likewise.
  Known gap to watch: an anti who stays outside the course region is only
  listed via their Runner/Anti answer, not by sightings.

## Earlier (2026-10-27, evening — owner test notes, round 1)

- The Swing "Mass started" popup is gone: it stole focus mid-game. Replaced by
  `ui/MassPromptOverlay` - an in-game box (top centre, movable) with Runner /
  Anti / Not today and a × to hide; clicks on it are consumed so they never
  reach the game. `MassParticipation.answer()` / `getPendingPrompt()` drive it.
- Sidebar redesigned in the dashboard palette + RuneScape fonts (`ui/Theme`):
  Agility ring (`ui/AgilityRing`, fills to 99 then "overcharges" inward to
  solid at 200M), name, diamond divider, status box (FC name only, joined
  tick/cross + join hint, mass running/elapsed, attend buttons or "Attending
  as X" + switch-role link), session / kit / runners-in-view cards, connection
  in small print at the bottom.
- Auto-joining the FC from the panel was asked for and **not built**: it would
  mean the plugin typing into the chat-channel join box for the player, which
  the Plugin Hub rejects as automation (and Jagex's third-party client rules
  forbid). The panel shows a join hint instead.

## Earlier (2026-10-27, end of day)

Everything below plus: per-role stints, rank-gated gear flags + over-head
overlay, in-course = attending, the loadout editor replaced by Settings gear
rules + Kits (local pre-course check, bank highlighting, 150k entry fee in every
kit). All pushed (`c88e8a9`), CI green, **repo now public**. Next: owner's full
in-game test pass against `DEV_SETUP.md` §5 (nothing ticked yet), then the
README rewrite, then Phase 9 (Hub submission). Create at least one kit on the
dashboard before testing or the plugin's kit dropdown is empty.

## Update 2026-10-27 — opt-in masses, roles, baked-in server

Built and deployed (migration `a7c3e91d5b24` applied; pre-deploy backup taken):

- **Opt-in per mass.** Dashboard "Start mass" → every plugin polls `/status`
  (now every 10s), shows a "Mass started" popup (Yes/No + Runner/Anti) and
  does nothing until Yes. Answer stored via `POST /api/v1/event/respond`
  (table `event_participants`); `/status` lists `participants`. Ingest from
  anyone not ATTENDING in the active mass is dropped server-side too.
- **Roles.** Anti = attendance time only (no laps/DD/loadout); observers skip
  antis and No-answerers; FC members with no plugin answer count as runners.
  Deaths are recorded for antis too (owner confirmed 2026-10-27). The
  course fee in every kit stays a fixed 150,000 coins, not a minimum (owner
  confirmed 2026-10-27).
- **Baked-in server.** URL + token compiled into `api/ServerConfig.java`, no
  in-game setting. The token was **rotated 2026-10-27** for this and is now
  committed in source (effectively public - protection is the opt-in rule).
  Dev override: `-Dagilitycc.apiUrl` / `-Dagilitycc.apiToken`.
- Also: dropped the unused account-hash upload; uvicorn trusts forwarded
  headers only from 192.168.1.130; Hub warning text updated.
- 94 server tests + plugin tests pass. Manual checklist in `DEV_SETUP.md` §5
  extended (nothing ticked yet). README rewrite still pending (do last).

## Where things stand (2026-10-06)

Phase 7 (deploy + hand back to owner) completed 2026-09-25. The system is
**live and in real in-game use**: `https://agilitycc.grestinator.com`,
repo `github.com/Scathis/AgilityCCTracker`, local checkout
`/mnt/120ssd/agilitycctracker`. FC group "Agility CC" exists (owner RSN
`Agility Cc`, corrected from an initial wrong guess — see BUILD_LOG). The
owner has been testing live in-game since Phase 7 and reporting real bugs/
feature requests as they come up.

**Phase 8 (OSRS dashboard reskin) is now fully closed out** — the owner
confirmed the dark wood-brown/gold theme renders correctly (v4 fix held),
then found and got fixed two more small visual bugs on the nav's Logout
button (illegible text, mismatched font — see "What this session did"
below). No further visual issues reported as of this update.

**Viewer role scope narrowed 2026-10-02** (owner-directed): Loadouts,
Players, and Raggers are no longer viewer-readable — viewer is now
Events + DD Leaderboard only, everything else (including those three
read-only pages) is staff+.

**No RuneLite Plugin Hub submission yet (Phase 9)** — explicitly gated on
finishing live in-game testing and re-confirming the DD/loadout observer
design with Hub maintainers first. Not started.

## What this session did, in order

(Full detail for everything up through the v4 CSS-specificity fix is in
`BUILD_LOG.md`'s Phase 8 section — this picks up right after that.)

1. **Owner visually confirmed the v4 theme fix held** (dark wood-brown/gold
   theme rendering correctly), closing out Phase 8's last open item — then
   immediately found two more small visual bugs on the nav's Logout button
   while looking at it.
2. **Fixed: Logout button text was invisible.** `body > nav button.secondary`
   set its idle text color (`--pico-secondary-inverse`) to `#3e3529`, the
   *exact same hex* as the nav bar's own background, with a transparent
   button background — so "Logout" was rendering in a color identical to
   what was behind it. Also fixed a second, smaller bug found while in
   there: the `:hover` rule set `--pico-secondary-background`, but Pico's
   hover state actually reads `--pico-secondary-hover-background` — wrong
   variable name, so it was a dead no-op silently falling back to Pico's
   stock blue-grey on hover. `asset_version` bumped to 5. Commit `a19d624`.
3. **Fixed: Logout button font didn't match the rest of the nav.** The
   RuneScape-font selector list only covered `body > nav strong, body >
   nav a` — the Logout `<button>` isn't either of those, so it fell back
   to Pico's sans-serif. Added `body > nav button` to that selector.
   `asset_version` bumped to 6. Commit `b75ff12`.
4. **Owner asked "can the different roles actually change things at all?"**
   — a request to confirm RBAC enforcement, not just trust the code. Read
   every route's `Depends()`: all mutating (POST) routes correctly require
   `STAFF` or `ADMIN` via `require_role()` + CSRF, so viewer (rank 0) can't
   reach any of them. But found the loadout slot-editing, raggers, and
   admin user/group routes were never actually *exercised* by a role-check
   test — only reasoned about from the `require_role()` call site. Added 4
   new tests that make real requests against a running test server and
   assert the 403s, closing that gap with executed proof instead of a code
   read. 84/84 passing. Commit `06d2f00`.
5. **Owner-directed scope change: narrowed viewer's read access** to
   Events + DD Leaderboard only. Loadouts, Players, and Raggers (previously
   viewer-readable, inconsistent with everything else in those sections
   already being staff+) bumped from `require_user` to
   `require_role(STAFF)` on their GET routes — list, detail, and the
   loadout item-search fragment. Nav bar also updated to hide those three
   links from viewers, so there's no dead link leading to a 403. Updated
   3 existing tests that used a viewer login purely to exercise rendering
   (not auth) to use staff instead, and added explicit 403/200 coverage
   for viewer vs. staff on all four paths. 86/86 passing. Commit `9a4fcc3`.

All five deployed (`docker compose -p agilitycctracker up -d --build api`
each time) and pushed to `origin/main`.

## Open items

- No open visual issues or feature requests as of 2026-10-06 — everything
  asked for this session (v4 theme confirmation, Logout button color/font,
  RBAC confirmation sweep, viewer scope narrowing) is implemented, tested,
  deployed, and documented in `BUILD_LOG.md`.
- Phase 9 (Plugin Hub submission) remains not-started, gated on finishing
  live in-game testing per `DEV_SETUP.md`'s manual checklist. Nothing new
  on this front this session — still the owner's call on when live
  testing is far enough along to pursue it.

## Quick reference for resuming

- **Local repo**: `/mnt/120ssd/agilitycctracker` (root has `server/` for
  the FastAPI app and the plugin source at repo root).
- **Test suite**: `cd server && docker compose -f docker-compose.test.yml
  build tests && docker compose -f docker-compose.test.yml run --rm tests`
  (then `down`). Compose file is under `server/`, not the repo root.
- **Deploy**: `cd server && docker compose -p agilitycctracker up -d
  --build api` (only rebuilds/recreates the API container; mysql stays
  untouched).
- **Static asset changes**: bump `ASSET_VERSION` in
  `server/app/web/templating.py` every time, or Cloudflare's edge cache
  will keep serving the old file at the same URL.
- **git push gotcha**: the sandboxed VS Code git-credential socket goes
  stale across session boundaries (`ECONNREFUSED .../vscode-git-*.sock`).
  Find the current live one with `ls -la /run/user/1000/ | grep
  vscode-git` (newest mtime) and retry with
  `VSCODE_GIT_IPC_HANDLE=/run/user/1000/vscode-git-<hash>.sock git push
  origin main`.
- **Backups**: `deploy/backup.sh`, run manually before any
  schema-migrating deploy, also cronned nightly at 04:00.
- Full docs: `docs/BUILD_LOG.md` (detailed history), `docs/BUILD_PLAN.md`
  (original spec), `docs/API.md`, `docs/DEV_SETUP.md`,
  `docs/SELF_HOSTING.md`, `docs/PLUGIN_HUB_SUBMISSION.md`,
  `docs/FUTURE_NON_HUB.md`.
