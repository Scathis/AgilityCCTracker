# AgilityCCTracker — Session Status

**Read this first when resuming.** This is a short handoff note: what the
system is now, what's left, and how to work on it. `docs/BUILD_LOG.md` has
the full history (one entry per change, very long) - rewrite this file at the
end of any session that changes things; don't append to it.

## Where things stand (2026-10-28)

**Feature-complete for a first Hub submission; in-game test pass next.**
Everything below is built, deployed (`https://agilitycc.grestinator.com`),
committed and pushed (`github.com/Scathis/AgilityCCTracker`, public; head
see `git log`). DB migration head `f6c1d9e2b735`. 114 server tests +
plugin tests pass; CI green. 2026-10-27/28 was one long session of owner test
notes (36 commits) - details in BUILD_LOG.md.

**Next, in order:**
1. Owner's in-game test pass against `docs/DEV_SETUP.md` §5 (rewritten
   2026-10-28, all unticked; sections A-O, O = the new alerts). Fix
   whatever fails.
2. Phase 9: open the Plugin Hub PR - manifest, `warning=` text and PR
   description are drafted in `docs/PLUGIN_HUB_SUBMISSION.md` (pre-flight
   list there is done: README, description, warning, icon).

## The design as it is now

**Plugin** (RuneLite, Java 11, repo root)
- **Install and go.** Server URL + token compiled into `api/ServerConfig.java`
  (token is public by design - security comes from opt-in, not the token).
  Never rotate it once the plugin is public: every install breaks until a new
  version is approved.
- **Opt-in per mass.** Dashboard "Start mass" -> an in-game prompt
  (`ui/MassPromptOverlay`, drawn in the game view, clicks consumed): Runner /
  Anti / Not today. Nothing is sent before opting in except the "is a mass
  running?" poll (RSN only). Being in the course during a mass counts as
  attending. "Not attending any more" in the panel leaves a mass.
- **Roles.** Runner: laps, DD, missing-gear checks. Anti: attendance + deaths
  only. Switching mid-mass makes separate per-role records (stints).
- **What's sent, only for the mass opted in to:** the player's own empty
  required slots (slot keys, never items), laps (tickets, lap time), death
  times; an "I'm here" heartbeat while in/around the course (3x3 regions,
  so antis count) with FC rank; for FC members seen *in* the course: name,
  FC rank, empty slots, DD tile timings. **Never:** worn items, inventory,
  skills/stats, world, position, the FC member list.
- **Local only:** kit check + bank outlines, Agility ring, previous sessions
  (last 30, per account) and fastest laps (per mass + all-time best).
- **Gear flags** (over-head labels + "Runners in view") only for FC members
  at/above the dashboard's flag rank.
- **Chat lines** (gear + kit) once on joining/logging in mid-mass, and on
  opening a bank only if something's missing.
- **Alerts** (`mass/MassNotices` + `ui/MassNoticeOverlay`, same in-game box
  style, OK or × dismisses, also posted as a red chat line): "Agility mass
  over" when staff stop the mass (attending + in the course, shown 2 min);
  "Mass bank starting" - "Make your way to the mage bank to secure your
  loot. 40+ laps is recommended" - when staff press Call mass bank (runners
  in the course during the next 60s, timed on the server clock). Long
  messages word-wrap.
- **Settings:** Mass (My role), FC observer, Display (panel, "Pre-mass check
  messages", gear flags, "Show pop-ups" on/off and "Pop-up size" 50-100% for
  the in-game boxes - the red chat lines go out either way). No
  Share options any more.
- **Panel** in the dashboard palette + RuneScape fonts: ring, name, all-time
  fastest lap, status box, This mass / Previous sessions tabs, Discord link.
- **Icons:** `icon.png` (Hub listing, 48x72) and `panel_icon.png` (sidebar
  button, 16x16), both from owner-supplied artwork (rope swing over spikes).

**Server + dashboard** (`server/`, FastAPI + MySQL 8.4)
- Attendance built only from course sightings; dashboard lists only people
  who turned up. Deaths are name + time.
- Accounts: self-registration ("Request one") -> pending -> admin approves
  with a role. Admins change roles, remove accounts, reset passwords
  (temporary password forces a new one). `/account` to change your own.
  Owner `grestinator` is protected; admins can't demote/remove themselves.
- Kits: full item list from RuneLite's cache dumps (incl. untradeables) with
  each item's worn slot - slot boxes only offer matching items; editor has
  gear on the left and a live game-style inventory (nothing stacks except
  the 150K coins fee, so 27 items max) on the right.
- Settings: runner gear rules, flag rank + observer switch, DD settings.
- Mass bank: "Call mass bank" button on the Events list and the event page
  (staff, only while a mass is running; stores `events.mass_bank_called_at`,
  shown as "Last called at"; sent to plugins as `active_event.mass_bank_at`).
- Gone: Players, Raggers, Loadouts pages; hiscores lookups; stored stats /
  equipment; FastAPI `/docs`. No per-request access log (IPs never written
  down); logs size-capped.

## Open items / decisions on record

- Owner decisions: 150K course fee is fixed (not a minimum); antis' deaths
  are recorded; lap times are kept; nothing in a kit inventory stacks.
- Not built, on purpose: FC auto-join (would be automation - Hub/Jagex
  rules); server-side session history (public token would expose everyone's).
- Ragger tracking may come back one day (`docs/FUTURE_NON_HUB.md`); nothing
  of it is in the code or DB now.
- Recon: `agilitycc.grestinator.com` is on the next external recon sweep
  (see `/containers/CLAUDE.md`).

## Quick reference

- **Repo:** `/mnt/120ssd/agilitycctracker` (plugin at root, server in
  `server/`). Push over HTTPS via the live VS Code git socket: try
  `/run/user/1000/vscode-git-*.sock` newest first, then
  `VSCODE_GIT_IPC_HANDLE=<sock> git push origin main`.
- **Plugin build:** `docker run --rm -u $(id -u):$(id -g) -v "$PWD":/p -w /p
  -e GRADLE_USER_HOME=/p/.gradle gradle:8.10-jdk11 ./gradlew build --no-daemon`
- **Server tests:** `cd server && docker compose -f docker-compose.test.yml
  build tests && docker compose -f docker-compose.test.yml run --rm tests;
  docker compose -f docker-compose.test.yml down -v >/dev/null 2>&1` (never
  pipe `down` into `tail` - SIGPIPE leaves the test DB behind). Lint:
  `ruff check` (CI runs check only). Test runs leave root-owned
  `__pycache__` dirs - remove via a throwaway root container.
- **Deploy:** run `deploy/backup.sh` first if there's a migration, then
  `cd server && docker compose -p agilitycctracker up -d --build api`
  (migrations run on start). Never touch gimhub's containers.
- **Static asset changes:** bump `ASSET_VERSION` in
  `server/app/web/templating.py` or Cloudflare keeps the old file.
- **Visual checks:** render plugin UI offscreen with a small Java harness
  (see BUILD_LOG "Panel layout fixes"); screenshot dashboard pages with a
  throwaway `zenika/alpine-chrome` container (host Chrome won't run
  headless). Remove the image afterwards.
- **Backups:** `deploy/backup.sh` (also nightly 04:00, 14-day retention in
  `server/backups/`).
- **Docs:** `BUILD_LOG.md` (history), `DEV_SETUP.md` (setup + test
  checklist), `API.md`, `PLUGIN_HUB_SUBMISSION.md`, `SELF_HOSTING.md`,
  `FUTURE_NON_HUB.md`, `BUILD_PLAN.md` (original spec).
