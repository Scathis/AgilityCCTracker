# AgilityCCTracker — Session Status

**Read this first when resuming.** `docs/BUILD_LOG.md` has the full detailed
history (one entry per phase/fix, very long); this file is a short
current-state summary plus pointers, specifically so a new session (or one
that's just been compacted) can get oriented fast without reading the whole
log. Update this file's "Where things stand" / "Open items" sections at the
end of any session that changes them — treat it like a handoff note, not an
append-only log (that's what BUILD_LOG.md is for).

## Where things stand (2026-10-01)

Phase 7 (deploy + hand back to owner) completed 2026-09-25. The system is
**live and in real in-game use**: `https://agilitycc.grestinator.com`,
repo `github.com/Scathis/AgilityCCTracker`, local checkout
`/mnt/120ssd/agilitycctracker`. FC group "Agility CC" exists (owner RSN
`Agility Cc`, corrected from an initial wrong guess — see BUILD_LOG). The
owner has been testing live in-game since Phase 7 and reporting real bugs/
feature requests as they come up, which is the bulk of what this session
covered (see below).

**No RuneLite Plugin Hub submission yet (Phase 9)** — explicitly gated on
finishing live in-game testing and re-confirming the DD/loadout observer
design with Hub maintainers first. Not started.

## What this session did, in order

1. **Loadout system redesign** (large feature, owner-directed structural
   change): replaced the old pattern-matching rule system
   (`check_type`/`args`/`when_json`) with a simpler per-slot
   `{required: bool, allowed_item_ids: list[int]}` model. Full server
   (models/schemas/services/routes/templates) + plugin
   (`loadout/Requirement.java`, `LoadoutEvaluator.java`) rewrite, new
   migration, 9 new plugin unit tests + server tests. Real icons via OSRS
   Wiki `Special:FilePath` resolver.
2. **Attendance auto-timeout feature**: `Attendance.last_sighted_at` +
   a 30s-poll worker that auto-closes an attendance record after 120s with
   no course sighting (owner's chosen threshold). New
   `course_sightings` field sent every upload cycle. Added a visible
   "Status" column (🟢 Present / ⚪ Away) to the attendance tab — owner had
   asked "how will I see my attendance session had closed?" and the honest
   answer was "you can't, currently," which is what prompted this.
3. **Confirmed working-as-designed (not a bug), twice**: the FC-member
   scanner/attendance heartbeat both require being physically *inside* the
   Wilderness Agility Course region, not just nearby. Owner explicitly
   chose to keep this strict: *"keep requiring being on the course, i
   think that will help with getting the plugin approved also as its a
   controlled area."*
4. **Real bug fix: loadout page search**. The search `<input>` was missing
   `name="q"` entirely, so HTMX never sent the typed text — every search
   silently returned the empty state. Fixed, plus layout fixes (search box
   was cramped/low-contrast). Added a regression test that greps rendered
   HTML for `name="q"` specifically, since the endpoint's own isolated test
   had already passed and hadn't caught the wiring bug.
5. **Ranked-member DD exclusion**: members with FC rank ≥ 0 (not
   `UNRANKED`) are now skipped entirely from DD tracking — owner said
   ranked members are "usually doing other duties" and "shouldn't be in
   the DD table in anyway."
6. **DD break-command regex widened** to match "break"/"brk" + "dd"
   anywhere in a message, order-independent (ranks say things like "break
   DD go run" mid-sentence) — was previously anchored to an exact phrase.
7. **Security audit fixes** (4 owner-provided findings): `/login` rate
   limiting, lap-time/ticket-count bounds validation (researched real OSRS
   values, not guessed), `Rsn` max-length-12 type alias applied everywhere,
   `hmac.compare_digest` for CSRF comparison.
8. **Gear/risk value + item-price removal** (owner-directed scope/security
   decision, triggered by an `items_worker` crash on an out-of-range GE
   price): deleted all gear-value/risk-value/ge_price/ha_value computation
   and storage, since nothing displayed it and it "reads as PK-target
   reconnaissance" — a Plugin Hub approval-risk concern. `items_worker` now
   only syncs id/name from the OSRS Wiki mapping.
9. **Phase 8 — OSRS-themed dashboard reskin** (owner-requested visual
   redesign), done in three rounds:
   - v1: RuneScape fonts (RuneLite's own bundled `runescape.ttf`/
     `runescape_bold.ttf` — owner explicitly chose the community font over
     a "properly licensed" alternative once told this dashboard won't be
     public), light-parchment Pico.css color overrides, vendored via the
     Dockerfile like every other frontend asset.
   - **Real bug found & fixed: Cloudflare edge-caching stale static
     assets.** The origin served updated CSS but Cloudflare kept serving
     the old cached copy at the same URL. Fixed properly (not a one-off
     purge, no dashboard access available anyway) via a Jinja global
     `asset_version` (`server/app/web/templating.py`) appended as `?v=N`
     to every vendored CSS/JS link in `base.html` — bump it on any future
     static-asset change.
   - v3: owner reported the light-parchment look was "too plain/pale,"
     asked for "something nicer but darker runescape in vibe" — reworked
     to a dark wood-brown canvas (`#241c12`) with gold text.
   - **v4 fix — the big one, just completed:** owner reported "nothing
     has changed" even in a fresh incognito window after the v3 deploy,
     ruling out caching. Root cause: Pico.css's own light-theme color
     block is scoped to `:root:not([data-theme=dark])`, which has
     *higher CSS specificity* than this project's plain `:root` override
     — since `base.html` sets `data-theme="light"`, Pico's own colors had
     been winning the whole time regardless of load order. **Every
     `--pico-*` custom-property override had been inert since the very
     first Phase 8 deploy**, not just the v3 darkening — served correctly
     (confirmed via curl repeatedly) but never actually applied in a real
     browser. Fixed by adding `!important` to every custom-property
     declaration in `osrs-theme.css`'s `:root` block (more robust than
     matching Pico's exact selector, which could change in a future Pico
     version). `asset_version` bumped to 4, 80/80 tests passing, deployed,
     committed (`7dd855f`) and pushed to `origin/main`.

## Open items

- **Awaiting the owner's own visual confirmation** that the dark
  wood-brown/gold theme now actually renders correctly in a real browser
  (the v4 fix above). Curl can confirm the CSS is served correctly but
  can't prove a browser's computed-style/specificity resolution — only
  an actual rendered-page check can. If still broken, re-check for
  *other* `:root`/variable-scoped Pico overrides this project's CSS
  might be fighting the same way (should be unlikely now that `!important`
  is used, but worth knowing as the next debugging angle if needed).
- No other feature requests are currently open — everything else asked
  for this session (loadout redesign, attendance timeout, search bug,
  DD fixes, security fixes, gear/risk removal) is implemented, tested,
  deployed, and documented in `BUILD_LOG.md`.
- Phase 9 (Plugin Hub submission) remains not-started, gated on finishing
  live in-game testing per `DEV_SETUP.md`'s manual checklist.

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
