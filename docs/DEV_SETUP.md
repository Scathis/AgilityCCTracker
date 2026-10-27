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

There is nothing to configure for the connection: the server address and
token are compiled into the plugin (`api/ServerConfig.java`), and there is no
in-game setting for either. To point a locally-run dev client at a test
server instead, add `-Dagilitycc.apiUrl=http://localhost:8090
-Dagilitycc.apiToken=<token>` to the run configuration's VM options.

Nothing is collected or uploaded until you pick **Runner** or **Anti** on the
in-game *Agility mass started* prompt (top of the game view) that appears when
a mass is started from the dashboard, or on the same buttons in the sidebar
panel. Your choice is remembered as **My role** in the plugin config.

## 5. Manual test checklist

Work through this in-game against the live server to confirm everything's
wired up end-to-end:

- [ ] Join the FC. Start a mass on the dashboard → within ~10s the in-game
      "Agility mass started" prompt appears at the top of the game view (no
      separate window, game keeps focus) plus an orange chat line; click
      **Runner** → your account shows up in that mass's attendance.
- [ ] Clicking the prompt's buttons never also clicks the game underneath
      (no walking/interacting). Alt+drag moves it; it stays where you put it.
- [ ] Run a lap on the Wilderness Agility course → a lap row appears on the
      dashboard, with tickets and a snapshot.
- [ ] Unequip your boots → you get a chat warning ("FC gear check: No boots"),
      and the dashboard shows the gap. Same for helm, amulet, body and legs
      (the defaults ticked on the dashboard Kits page).
- [ ] Get a second account/friend into view wearing a crossbow with no
      shield → the dashboard shows SHIELD for them (over-head label "No
      shield"); with a bow and no shield they're fine; with a non-bow weapon
      (or nothing) they show "No bow".
      Check the server logs / request body only ever contains requirement
      *keys* (e.g. `run-xbow-shield`), never raw item ids for another
      player.
- [ ] Have a ranked account type `dd` in FC chat. One account stands on a
      DD tile, another keeps running. Type `break dd` → the DD tab on the
      dashboard shows arrival time, time on tile, and correctly flags the
      one that kept running. The leaderboard updates.
- [ ] Stop the event.

### Opt-in and roles (new 2026-10-27)

- [ ] Before answering the prompt (or after **Not today**), *outside the
      course*: die and move about → nothing reaches the dashboard, the panel
      says "Are you attending?" / "Not attending", and nothing is scanned.
- [ ] Close the prompt with its × → nothing is sent; the sidebar's Runner /
      Anti buttons still answer.
- [ ] Answer **Not today** (or close the prompt), then walk into the course →
      within a tick you're marked attending with your configured role, a chat
      line says so, and the dashboard shows you. Same if you never answered.
- [ ] In the course but in a **different** friends chat → not auto-marked.
- [ ] Answer Yes as **Runner** → laps, DD and own-gear warnings all work as in
      the checks above.
- [ ] Answer Yes as **Anti** → attendance time shows (Role column says Anti),
      but no laps, no DD presence, no loadout warnings, and the dashboard
      Loadout column says "n/a (anti)". Dying in the course as an anti still
      shows up on the dashboard's deaths.
- [ ] Switch role mid-mass with the panel's "Switch to …" link (or **My
      role** in the plugin config), e.g. Anti → Runner → Anti → the dashboard Attendance tab shows a separate row per role, each
      with its own time present and laps (never combined); runner-only
      behaviour switches on/off.
- [ ] Log out and back in mid-mass → no prompt; you're restored as attending
      with the same role.
- [ ] Stop the mass, start another → prompt appears again; the old answer does
      not carry over.
- [ ] Sidebar panel (new look): Agility ring shows your level (fills to 99;
      past 99 a brighter second lap + inward fill, solid at 200M - hover for
      exact xp); your name; FC box says "Agility CC" with a green tick /
      "Joined" in the FC and a red cross / "Not joined" + join hint outside it;
      mass row shows "Running - Nm" or "No mass running"; connection status
      and an "Agility CC Discord" link (opens discord.gg/agility) in small
      print at the bottom. Nothing is cut off or causes a sideways scrollbar.
- [ ] "Switch to Anti/Runner" is a gold-edged pill that fills on hover.
- [ ] **Previous sessions** tab: after a mass, it lists that mass with its
      date, role(s), laps (runners) and time on course. It survives a client
      restart and is per account (log into another account → its own list).
- [ ] The pre-mass kit check chat line and the mass prompt chat lines are
      red and readable.
- [ ] Bank highlighting traces the missing items' own outline, not a box.
- [ ] Dashboard Attendance tab: starting a mass no longer lists the whole FC
      - only people seen at the course, who picked Runner/Anti, or ran a lap.
- [ ] An observer standing next to an **anti** and a **No**-answerer doesn't
      report either of them for gear/DD; a friend without the plugin is still
      gear-checked as a runner.
- [ ] No API URL / token is visible or editable anywhere in the plugin config.

### Kits and the pre-course check (new 2026-10-27)

- [ ] Dashboard → Settings → **Runner gear rules**: change which slots must
      not be empty, untick/tick the bow/crossbow rule, save → takes effect
      in-game within ~10s. (Admin only; staff can't see Settings.)
- [ ] Settings page is grouped into cards (gear rules, gear flags & observer,
      DD calls, DD tiles) with one Save at the bottom; "Settings saved" shows.
- [ ] Kits → **Create kit**: name box at the top, pick gear per slot and add
      inventory lines - nothing is saved until **Save kit** at the bottom
      (leaving with unsaved changes warns you; Cancel discards).
- [ ] The first kit becomes the default; **Mark as default** on another row
      moves it; **Edit** reopens a kit with everything filled in; deleting the
      default hands it to another kit.
- [ ] In-game sidebar: the "My kit" dropdown lists the kits with the default
      pre-selected; your choice is remembered after a client restart.
- [ ] Attend a mass as a **Runner** while away from the course → one orange
      chat line ("Pre-mass check (<kit>): N thing(s) missing…" or "all set"),
      and the sidebar lists exactly what's missing ("Wear: …", "Bring: … x2").
- [ ] Open your bank → the missing items are outlined; take them out / equip
      them and the outlines and sidebar lines disappear.
- [ ] Every kit shows a locked "Coins 150,000 - course entry fee" line in the
      editor; with fewer than 150k coins in your inventory the pre-course check
      says "Bring: Coins x…" and outlines coins in your bank.
- [ ] Any dose of a kit potion counts (e.g. Stamina potion (2) for a (4) in the
      kit); quantity counts potions, not doses.
- [ ] As an **Anti**, or once you're in the course → no kit check, no outlines.
- [ ] Nothing about your inventory reaches the server (dashboard player page
      shows worn gear only, as before).
- [ ] DD tab on the dashboard no longer has a "Loadout gaps" column.

### Gear flags and who can see them (new 2026-10-27)

- [ ] As a **ranked** member (rank ≥ the threshold on dashboard Settings) at
      the course during a mass: a red label ("No helm, boots") appears over
      each runner with missing gear, and the sidebar's "FC members in view"
      list fills in.
- [ ] Runners with full gear show no label; antis and members who answered No
      never get a label.
- [ ] As an **unranked** member (or with the threshold raised above your
      rank): no labels, an empty "FC members in view" list - but you still get
      your own gear warning, and the dashboard still shows your scan results.
- [ ] Turn off **Show gear flags** in the plugin config → labels disappear.
- [ ] Standing in a different friends chat (not Agility CC) → nothing is
      scanned and no flags show.
- [ ] Change **Lowest FC rank that sees gear flags** on the dashboard Settings
      page → takes effect within ~10 seconds, no plugin restart.

### Plugin, additional in-game checks

- [ ] Die in the Wilderness → a death appears on the event's Deaths tab
      (name and time only).
- [ ] Attendance builds from sightings only: someone in the FC who never
      comes near the course never appears (not even as a hidden row); an anti
      working the area just outside the course still clocks time present.
- [ ] Fastest lap: run two or more laps → "Fastest lap" (this mass) and
      "Best ever" show in This session; the Previous sessions card shows the
      mass's fastest; Best ever survives a client restart.
- [ ] Server logs (`docker logs agilitycctracker-api-1`) show no per-request
      lines - no IPs or RSNs.
- [ ] Pre-mass check chat lines (gear + kit) appear once when you join the
      mass and once on logging in mid-mass - not on gear swaps or world hops;
      opening a bank repeats them only if something's still missing.
- [ ] "Not attending any more" in the panel: you drop to "Not attending", the
      Runner/Anti buttons come back, and nothing more is tracked - unless you
      walk into the course, which marks you attending again.
- [ ] Plugin settings read correctly: Mass / FC observer / Display sections;
      no Share stats/gear/roster options; "Pre-mass check messages" toggle.
- [ ] Sidebar panel: status and the "Runners in view" list show.
- [ ] Config toggles: turn off FC observer
      (other members stop being scanned); turn off Loadout chat warnings
      (boots warning disappears); turn off Show sidebar panel (panel goes away).
- [ ] Blank API URL, or a wrong token → plugin sits idle without spamming
      errors; a wrong token shows as invalid in the panel.
- [ ] Stand outside the course region with the observer on → no other players
      are scanned or reported.
- [ ] Ranked members are excluded from DD presence.
- [ ] Switch accounts / log out and back in → state resets cleanly, nothing
      carries over from the previous account.
- [ ] At the course with no event running → nothing is scanned; during an
      active event, attendance doesn't time out while you're still sighted at
      the course.

### Dashboard

- [ ] Login/logout works; 5 wrong passwords gets rate-limited.
- [ ] Event Attendance tab shows rank, time present, plugin user, loadout
      status and laps; Laps tab shows the leaderboard and chart.
- [ ] DD tab: staff can edit call/break times; leaderboard top-3 card and CSV
      export work.
- [ ] Manual gap form logs a staff-entered gap for an attendee.
- [ ] Loadouts: edit a slot's required items, search an item by name, add
      one, delete it.
- [ ] No "Raggers" or "Players" anywhere on the dashboard (both removed
      2026-10-27); the event page's tab is just "Deaths", and names in tables
      are plain text.
- [ ] Settings: save DD tiles, regexes, min rank, grace periods, max minutes,
      crossbow-counts-as-bow and the observer kill switch; turning the kill
      switch off stops plugin scanning on its next status poll with no plugin
      update.
- [ ] Admin: create a user, reset a password, rotate the token (shown once,
      old token stops working).
- [ ] Roles: viewer sees only Events + DD Leaderboard; staff sees everything
      except Admin.
- [ ] Phone-width rendering is usable.

### Server (checked by Claude)

- [ ] Item-name worker running (feeds the Kits item search).
- [ ] Nightly 04:00 backup has run.

## 6. Testing without an IDE (beta testers)

```bash
./gradlew shadowJar
java -jar build/libs/agility-cc-tracker-*-all.jar --developer-mode
```

Same one-time Jagex credentials step from §3 applies.
