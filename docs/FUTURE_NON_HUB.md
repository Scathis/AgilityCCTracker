# Ideas deliberately NOT implemented (and why)

This documents things that came up while designing this project that would
be genuinely useful but can't or shouldn't go into the Hub-distributed
plugin. Written down so they don't get silently re-proposed later without
this context, and so it's clear which limits are real (Hub rules, Jagex
policy) versus which are just "not built yet."

## Automated ragger detection

The idea: a sideload-only module that automatically flags a suspicious
player near the FC without a staff member manually adding them.

Sketch of how it *could* work, if it were ever built as a sideload-only
tool:
- `PlayerSpawned` while in the Wilderness Agility region (11837) where
  `!isFriendsChatMember()` — i.e. someone who isn't in the tracked FC.
- `getCombatLevel()`, visible worn gear via
  `PlayerComposition.getEquipmentIds()` (excluding ring/ammo slots, which
  aren't visible on other players anyway), `getSkullIcon()`.
- `InteractingChanged` / `HitsplatApplied` / `ActorDeath` to attribute an
  attack on an FC member to a specific attacker.

**Why this can never go on the Plugin Hub:**
- The Hub's crowdsourcing/scope rules don't cover "surveil arbitrary nearby
  players and report on them" as a use case, regardless of intent.
- It reads far more about other players than the one narrow,
  gap-only exception this project's actual Hub-bound plugin relies on
  (`scan/FcMemberScanner`) — there's no way to shrink this down to the same
  "only derived booleans/keys, never raw data" shape, because the entire
  point is identifying and describing a specific hostile player.
- Real Jagex third-party-client-guideline risk: automated tracking/logging
  of other players' identifying info and movements, even just locally, is
  the kind of thing that draws scrutiny independent of whether it's ever
  uploaded anywhere.
- Data retention/privacy: even kept purely local, this is a log of
  identifiable information about people who never opted into anything.

## Uploading other players' actual worn items

Not allowed, full stop — not just for the Hub-distributed plugin, for this
project's design as a whole. Only loadout **gaps** (which named requirement
a player failed) are ever meant to leave a client. This line is enforced by
the payload-shape test in the plugin's test suite, not just documented
intent.

## How sideloading actually works (for context, not as a recommendation)

- Only RuneLite's developer mode, or a client built and run from source,
  can load a plugin jar that never went through the Hub.
- Jagex accounts need the same one-time
  `--insecure-write-credentials` workaround described in `DEV_SETUP.md` to
  log in through a dev-mode client at all.
- Unofficial/third-party clients carry real account-standing risk with
  Jagex — this isn't specific to this project, it's true of any sideloaded
  RuneLite plugin.

## The Hub-safe manual alternative

If ragger-tracking ever becomes a real, wanted feature: a staff-only
hotkey or right-click menu entry that copies a player's RSN to the system
clipboard (same mechanism as the existing "Copy my tile" button) for
pasting into the dashboard's Raggers page by hand. No automatic
surveillance, no Hub-rule conflict — a human decides who gets flagged, the
plugin just saves a bit of typing.
