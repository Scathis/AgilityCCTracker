# RuneLite Plugin Hub submission

**Status 2026-10-28:** owner's in-game test pass done; pre-flight checks
below done; PR being opened from the owner's fork (no `gh` CLI on the host,
so the fork and the PR itself are created in the browser; Claude pushes the
manifest branch over the VS Code git socket).

## 1. Repo visibility

**Done 2026-10-27** - the repo is public. (The Hub builds plugins straight
from their source repo, so it had to be.) `server/` living in the same repo as the plugin is fine — no
secrets are committed anywhere in it (`.env`, backups, and anything
token/password-shaped are all gitignored and always have been throughout
this build).

## 2. Pre-flight checklist

- [x] `runelite-plugin.properties` present and correct (description updated
      2026-10-27; the `warning=` line lives in the plugin-hub manifest below)
- [x] `icon.png` - done 2026-10-28: gold runner on the rope swing over the
      spikes (the Wilderness course's first obstacle). Owner generated it with
      another AI at 1024x1536; the painted checkerboard was keyed out (dark
      outline makes it clean) and it was box-downscaled to 40x72 centred on a
      transparent 48x72 canvas (the Hub's maximum). The large original is not
      committed.
- [x] `LICENSE` present (BSD-2-Clause; holder line renamed 2026-10-28)
- [x] **`README.md` rewritten 2026-10-27** - players' guide (prompt, roles,
      panel, kits, missing-gear checks, settings, what's shared and when), organisers'
      dashboard summary, repo layout. Keep it in step with later changes.
- [x] `./gradlew build` passes clean on Java 11 (2026-10-28)
- [x] The §5.11 Hub-readiness grep checks still pass (2026-10-28; the only
      hit is `java.lang.reflect.Type` for Gson's `TypeToken` in `SessionHistory`, not reflection) (no `getPlayers()`
      outside `scan/FcMemberScanner`, no banned event subscriptions, no
      reflection/subprocess/manual `OkHttpClient`/`Gson` construction)

## 3. Open the PR

1. Fork `runelite/plugin-hub`.
2. Add `plugins/wildy-agility-mass`:
   ```
   repository=https://github.com/Scathis/AgilityCCTracker.git
   commit=<full 40-char commit sha to submit>
   warning=This plugin sends your RSN and IP address to a server not controlled or verified by the RuneLite developers, to check whether the Agility CC friends chat has a mass running. Only once you opt in to a mass (by picking Runner or Anti, or by entering the Wilderness Agility Course during it), and only for that mass, it also sends: that you are near the course and your friends chat rank, which of your required gear slots are empty, and your Wilderness Agility laps and deaths; and, for friends chat members you can see in the course, their names and ranks, which required gear slots they have empty, and their time on the chat's DD tiles during a DD call. No one's worn items, inventory, stats, world or position are ever sent, and the friends chat member list is not sent.
   ```

## 4. PR description (ready to paste)

Title: `Add Wildy Agility Mass`

```markdown
Adds **Wildy Agility Mass** - a tool for the Agility CC friends chat's
Wilderness Agility Course masses: opt-in attendance, laps, missing-gear
checks, recommended kits and DD call timing, plus a local Agility goal /
ticket tracker. Source: https://github.com/Scathis/AgilityCCTracker

**External server.** It talks to the FC's own server
(`agilitycc.grestinator.com`); the URL and a non-secret token are compiled in.
The `warning=` line lists exactly what is sent.

**Opt-in per mass.** Until a player opts in, the only request is "is a mass
running?" (with their RSN). When organisers start a mass, an in-game prompt
(drawn in the game view, never a separate window) offers Runner / Anti /
Not today; walking into the course during the mass also counts as attending,
and the player is told so in chat. The server ignores anything from a player
who hasn't opted in to the current mass. Antis are attendance time only.

**Other players.** Exactly one class, `scan/FcMemberScanner`, reads other
players, and only:
- while the local player is inside the Wilderness Agility Course region,
- for members of the tracked friends chat (never antis or people who said no),
- uploading only their name, FC rank, which *required* gear slots are empty
  (slot keys like `BOOTS`, `WEAPON`) and DD-tile durations.
Never worn items, item ids, coordinates, world or the member list. The FC
owner has a server-side switch to turn this off without a plugin update.
`GearFlagOverlay` only draws the players the scanner hands it.

**Gear flags are rank-gated.** The over-head icon/label and the sidebar list
of runners missing gear are only shown to FC members at or above a rank the
organisers set, so a stranger installing the plugin gets no target list.

**Local only.** The kit check (own worn items, inventory, bank highlighting),
the Agility goal card (own XP, carried and banked ticket counts) and session
history are computed and kept on the player's machine and never uploaded.

**No** reflection, subprocesses, or self-built HTTP/JSON clients (injected
`OkHttpClient`/`Gson`), and no third-party dependencies.
```

Background for any maintainer questions (not in the PR body): the
empty-slots-only design (an earlier build uploaded the player's own worn
items; removed 2026-10-27 and the stored data deleted); no gear/risk value
calculation, deliberately (removed 2026-10-01 - reads as PK reconnaissance);
`rogue-chests` as Hub precedent for "reads other players, uploads only a
derived signal".

## 5. After submission

Watch the "RuneLite Plugin Hub Checks" CI run on the PR. Fix issues and
push new `commit=` shas to the *same* PR rather than opening a new one.
Later plugin updates (post-acceptance) are a new PR bumping `commit=` in
this same `plugins/wildy-agility-mass` file — the plugin's own repo history
doesn't need to change shape for this. Confirmed against the plugin-hub
README 2026-10-27: branch from `upstream/master`, change only `commit=`, open
a PR; every update is reviewed again (new third-party dependencies slow that
a lot - there are none). Players pick updates up automatically from the Hub.
Server-side changes (gear rules, kits, flag rank, observer switch,
dashboard) need no plugin update; never rotate the baked-in token once the
plugin is public, as every installed copy breaks until an update ships.
