# RuneLite Plugin Hub submission

**Not yet executed.** This is Phase 9 in `BUILD_PLAN.md`, deliberately held
until after real in-game testing (`DEV_SETUP.md`'s manual checklist) and
until the owner has re-confirmed the DD/loadout observer design with the
RuneLite plugin-hub maintainers — the crowdsourcing/"one class may read
other players" rule is exactly the kind of thing that's worth a maintainer
sanity-check before a PR, not after.

## 1. Repo visibility

**Done 2026-10-27** - the repo is public. (The Hub builds plugins straight
from their source repo, so it had to be.) `server/` living in the same repo as the plugin is fine — no
secrets are committed anywhere in it (`.env`, backups, and anything
token/password-shaped are all gitignored and always have been throughout
this build).

## 2. Pre-flight checklist

- [x] `runelite-plugin.properties` present and correct (description updated
      2026-10-27; the `warning=` line lives in the plugin-hub manifest below)
- [ ] `icon.png` - still the generated placeholder; owner is sourcing real
      artwork (must stay `icon.png` at the repo root, 48x72 per the Hub template)
- [ ] `LICENSE` present (BSD-2-Clause)
- [x] **`README.md` rewritten 2026-10-27** - players' guide (prompt, roles,
      panel, kits, gear checks, settings, what's shared and when), organisers'
      dashboard summary, repo layout. Keep it in step with later changes.
- [ ] `./gradlew build` passes clean on Java 11
- [ ] The §5.11 Hub-readiness grep checks still pass (no `getPlayers()`
      outside `scan/FcMemberScanner`, no banned event subscriptions, no
      reflection/subprocess/manual `OkHttpClient`/`Gson` construction)

## 3. Open the PR

1. Fork `runelite/plugin-hub`.
2. Add `plugins/agility-cc-tracker`:
   ```
   repository=https://github.com/Scathis/AgilityCCTracker.git
   commit=<full 40-char commit sha to submit>
   warning=This plugin sends your RSN and IP address to a server not controlled or verified by the RuneLite developers, to check whether the Agility CC friends chat has a mass running. Only once you opt in to a mass (by picking Runner or Anti, or by entering the Wilderness Agility Course during it), and only for that mass, it also sends: your world, combat and skill levels, worn equipment, wilderness level and skull status, Wilderness Agility laps and deaths; the names, ranks and worlds of the friends chat's members; and, for chat members seen at the course, their names, which required gear slots they have empty, and their time on the chat's DD tiles during a DD call.
   ```

## 4. PR description

Should cover, explicitly:

- **Opt-in per mass.** The plugin does nothing (beyond polling the server for
  whether a mass is running) until the player picks Runner or Anti on an
  in-game prompt (drawn inside the game view, never a separate window) that
  appears when the FC's organisers start a mass - or walks into the Wilderness
  Agility Course while in that friends chat during the mass, which counts as
  attending (they're told so in chat). The server also ignores
  everything from a player who hasn't opted in. Antis (a player-chosen role)
  are attendance-time only: no laps, no DD, no gear checks, and observers
  never report on antis or on players who answered No.
- **Gear flags are rank-gated.** What a player wears is public in-game, but
  the plugin only shows a ready-made list / over-head labels of other players'
  gaps to friends chat members at or above an FC rank the organisers set
  (default: any ranked member), so a stranger who installs the plugin gets no
  target list. The scan itself only runs inside the tracked friends chat, and
  the over-head overlay is passed only the flagged players by
  `FcMemberScanner`; it reads nothing from them.
- **Kit check is local.** The pre-course "are you ready?" check reads the
  player's *own* worn items and inventory to compare with a recommended kit
  and outline missing items in their bank; none of it is uploaded (the
  inventory is never sent at all).
- **Fixed server address.** The server URL and token are compiled in (this FC
  tool has exactly one backend); the token is not a secret and is not relied
  on for security - the opt-in rule above is.

- The **gaps-only loadout design** — other players' loadout compliance is
  reported as which named slots they failed (e.g. `"WEAPON"`), never
  their actual worn items. Reference the `rogue-chests` plugin as an
  existing Hub precedent for "reads other players, uploads only a
  derived signal, not raw data."
- That exactly **one class** (`scan/FcMemberScanner`) ever reads other
  players' state — this is enforced by the §5.11 grep checks, not just
  convention.
- The **payload-shape test** that proves the "no other player's raw
  data leaves the client" boundary in code, not just in review.
- The **DD observer**: booleans and durations only (arrival lateness, time
  on tile, whether they kept running, whether their DD loadout failed —
  never coordinates or raw gear), with a server-side `observer_enabled`
  kill switch the FC owner controls independent of any plugin update.
- **Scope is physically bounded, not just logically bounded.**
  `FcMemberScanner` doesn't just limit *what* it uploads about other
  players (gap keys, not raw gear) — it refuses to scan anyone at all
  unless the reporting player is themselves standing inside the
  Wilderness Agility Course region (confirmed deliberately kept strict
  2026-10-01, not loosened to "nearby" even though that would have made
  in-game testing marginally more convenient). A player a world away, or
  even just outside the course boundary, is invisible to this plugin —
  there's no way to use it as a general-purpose "who's near me" scanner.
- **No gear/risk value calculation, deliberately.** An earlier version
  computed each player's own worn-gear GP value and total at-risk value
  (self only, never other players) for a possible future dashboard
  display that was never actually built. Removed entirely 2026-10-01,
  before ever being used for anything - a tool that estimates how much
  GP someone is carrying reads as PK-target reconnaissance regardless of
  intent, and this project has no legitimate use for it worth that
  optics risk.

## 5. After submission

Watch the "RuneLite Plugin Hub Checks" CI run on the PR. Fix issues and
push new `commit=` shas to the *same* PR rather than opening a new one.
Later plugin updates (post-acceptance) are a new PR bumping `commit=` in
this same `plugins/agility-cc-tracker` file — the plugin's own repo history
doesn't need to change shape for this. Confirmed against the plugin-hub
README 2026-10-27: branch from `upstream/master`, change only `commit=`, open
a PR; every update is reviewed again (new third-party dependencies slow that
a lot - there are none). Players pick updates up automatically from the Hub.
Server-side changes (gear rules, kits, flag rank, observer switch,
dashboard) need no plugin update; never rotate the baked-in token once the
plugin is public, as every installed copy breaks until an update ships.
