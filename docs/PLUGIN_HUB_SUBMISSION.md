# RuneLite Plugin Hub submission

**Not yet executed.** This is Phase 9 in `BUILD_PLAN.md`, deliberately held
until after real in-game testing (`DEV_SETUP.md`'s manual checklist) and
until the owner has re-confirmed the DD/loadout observer design with the
RuneLite plugin-hub maintainers — the crowdsourcing/"one class may read
other players" rule is exactly the kind of thing that's worth a maintainer
sanity-check before a PR, not after.

## 1. Repo visibility

The Hub builds plugins straight from their source repo, so this repo
(`github.com/Scathis/AgilityCCTracker`) needs to be **public** before
submitting. `server/` living in the same repo as the plugin is fine — no
secrets are committed anywhere in it (`.env`, backups, and anything
token/password-shaped are all gitignored and always have been throughout
this build).

## 2. Pre-flight checklist

- [ ] `runelite-plugin.properties` present and correct (author, description,
      warning, etc.)
- [ ] `icon.png` present (currently a generated placeholder — consider
      replacing with real artwork before submitting)
- [ ] `LICENSE` present (BSD-2-Clause)
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
   warning=This plugin sends your RSN, stats, worn equipment, inventory value, wilderness agility laps and deaths, the names, ranks and worlds of members of the configured friends chat, whether nearby friends chat members meet the chat's required loadout and whether they are on the chat's DD tiles during a DD call, and your IP address to a server not controlled or verified by the RuneLite developers.
   ```

## 4. PR description

Should cover, explicitly:

- The **gaps-only loadout design** — other players' loadout compliance is
  reported as which named requirements they failed (e.g.
  `run-xbow-shield`), never their actual worn items. Reference the
  `rogue-chests` plugin as an existing Hub precedent for "reads other
  players, uploads only a derived signal, not raw data."
- That exactly **one class** (`scan/FcMemberScanner`) ever reads other
  players' state — this is enforced by the §5.11 grep checks, not just
  convention.
- The **payload-shape test** that proves the "no other player's raw
  data leaves the client" boundary in code, not just in review.
- The **DD observer**: booleans and durations only (arrival lateness, time
  on tile, whether they kept running, whether their DD loadout failed —
  never coordinates or raw gear), with a server-side `observer_enabled`
  kill switch the FC owner controls independent of any plugin update.

## 5. After submission

Watch the "RuneLite Plugin Hub Checks" CI run on the PR. Fix issues and
push new `commit=` shas to the *same* PR rather than opening a new one.
Later plugin updates (post-acceptance) are a new PR bumping `commit=` in
this same `plugins/agility-cc-tracker` file — the plugin's own repo history
doesn't need to change shape for this.
