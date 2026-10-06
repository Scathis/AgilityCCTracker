# AgilityCCTracker — Session Status

**Read this first when resuming.** `docs/BUILD_LOG.md` has the full detailed
history (one entry per phase/fix, very long); this file is a short
current-state summary plus pointers, specifically so a new session (or one
that's just been compacted) can get oriented fast without reading the whole
log. Update this file's "Where things stand" / "Open items" sections at the
end of any session that changes them — treat it like a handoff note, not an
append-only log (that's what BUILD_LOG.md is for).

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
