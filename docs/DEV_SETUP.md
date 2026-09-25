# Dev / in-game testing setup

This is for whoever is running the plugin against the live tracker (currently
`https://agilitycc.grestinator.com`) to test in-game, or developing/reviewing
plugin code — not something a normal FC member needs to do; they just install
the plugin from the Hub (once it's submitted, see `PLUGIN_HUB_SUBMISSION.md`).
No need to clone the RuneLite repo — Gradle pulls the released client.

## 1. Prerequisites

- Git
- JDK 11 (Eclipse Temurin 11 recommended)
- IntelliJ IDEA Community (or any IDE with Gradle support)

## 2. Get the code running

1. Clone this repo.
2. Open it in IntelliJ as a Gradle project. Set the project SDK to Java 11.
3. Run the Gradle `run` task, or run `AgilityCCTrackerPluginTest.main`
   directly (`src/test/java/com/agilitycctracker/AgilityCCTrackerPluginTest.java`).
   This launches RuneLite in developer mode with "Wildy Agility FC Tracker"
   already loaded — no need to install anything into a normal RuneLite
   client for testing.

## 3. Logging in (one-time Jagex account setup)

RuneLite's developer-mode client needs a one-time credentials step for
Jagex accounts (legacy accounts can just log in directly, skip this):

1. In the official RuneLite launcher, go to Configure and add
   `--insecure-write-credentials` to the client arguments.
2. Launch it once and log in via the Jagex Launcher. This writes
   `~/.runelite/credentials.properties`, which the dev client (from step 2
   above) will reuse automatically.
3. **Delete `~/.runelite/credentials.properties` once you're done testing** —
   it's a plaintext credential file and shouldn't be left lying around
   longer than needed.

## 4. Configure the plugin

Open the plugin's config panel in RuneLite and set:

- **API URL:** `https://agilitycc.grestinator.com`
- **API token:** the token you were given when the FC group was created
  (printed once at `create-group` time — ask whoever ran that command if you
  don't have it; it is never written into this repo or any committed file,
  and there's no way to retrieve it after the fact — only rotate it via
  `python -m app.cli rotate-token <group_id>`, which invalidates the old one).

## 5. Manual test checklist

Work through this in-game against the live server to confirm everything's
wired up end-to-end:

- [ ] Join the FC. Start an event on the dashboard → your account shows up
      in that event's attendance.
- [ ] Run a lap on the Wilderness Agility course → a lap row appears on the
      dashboard, with tickets and a snapshot.
- [ ] Unequip your boots → you get a chat warning, and the dashboard shows
      the loadout gap.
- [ ] Get a second account/friend into view wearing a crossbow with no
      shield → the dashboard shows "Crossbow without shield" for them.
      Check the server logs / request body only ever contains requirement
      *keys* (e.g. `run-xbow-shield`), never raw item ids for another
      player.
- [ ] Capture both DD tiles in-game using the panel's "Copy my tile" button,
      then paste each into Settings on the dashboard.
- [ ] Have a ranked account type `dd` in FC chat. One account stands on a
      DD tile, another keeps running. Type `break dd` → the DD tab on the
      dashboard shows arrival time, time on tile, and correctly flags the
      one that kept running. The leaderboard updates.
- [ ] Stop the event.

## 6. Testing without an IDE (beta testers)

```bash
./gradlew shadowJar
java -jar build/libs/agility-cc-tracker-*-all.jar --developer-mode
```

Same one-time Jagex credentials step from §3 applies.
