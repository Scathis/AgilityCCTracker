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

Rewritten 2026-10-28 to match everything built up to then (all unticked).
Work through it in-game against the live server. You'll want: a ranked
account and an unranked one in the Agility CC FC, a second account or friend
(with and without the plugin), a dashboard admin login, and at least one kit
on the dashboard. Pull and rebuild the plugin first.

### A. Mass start and the in-game prompt

- [ ] Start a mass on the dashboard → within ~10s an **"Agility mass
      started"** box appears at the top of the game view (no separate window;
      the game keeps focus) plus a **red** chat line.
- [ ] The box's buttons (**Runner** / **Anti** / **Not today**) work, and a
      click on them never also clicks the game underneath. The **×** hides it
      without answering. Alt+drag moves it.
- [ ] Clicking **Runner** → you appear on the event's Attendance tab with
      Role "Runner".
- [ ] Stop the mass and start another → the prompt appears again; the old
      answer doesn't carry over.
- [ ] Log out and back in mid-mass → no prompt; you're restored as attending
      with the same role.

### B. Opt-in, roles and leaving a mass

- [ ] Before answering (or after **Not today**), outside the course: move
      about, die → nothing reaches the dashboard and nothing is scanned; the
      panel says "Are you attending?" / "Not attending".
- [ ] After **Not today** or ×, walk into the course → you're marked attending
      with your saved role within a tick, a red chat line says so, and the
      dashboard shows you. Same if you never answered.
- [ ] In the course but in a **different** friends chat → not auto-marked.
- [ ] As **Anti**: attendance time builds (Role "Anti"), but no laps, no DD
      presence, no gear checks (Loadout column "n/a (anti)"); a death still
      shows on the Deaths tab.
- [ ] **Switch to Anti / Switch to Runner** (gold pill in the panel) mid-mass
      → the Attendance tab shows a separate row per role, each with its own
      time and laps; runner-only things switch on/off.
- [ ] **Not attending any more** (link under the role row) → you drop to "Not
      attending", the Runner/Anti buttons come back and nothing more is
      tracked - unless you walk into the course, which marks you attending
      again.

### C. Sidebar panel

- [ ] Agility ring shows your level (fills to 99; past 99 a brighter second
      lap and an inward fill, solid at 200M); hover shows exact xp.
- [ ] Name at the top; under it "Agility - …xp" and, once you have one, your
      all-time **Fastest lap**.
- [ ] Diamond divider, then the status box: **Friends chat** "Agility CC" with
      a green tick / "Joined", or a red cross / "Not joined" plus "Join from
      the chat-channel tab: Agility Cc"; **Mass** "Running - Nm" or "No mass
      running"; **Attending as** "Runner | Switch to Anti" (or "Are you
      attending?" with Runner/Anti buttons).
- [ ] Section titles are gold and bigger than the text under them; no stray
      line between Friends chat and Mass; nothing overlaps or is cut off and
      there's no sideways scrollbar.
- [ ] Footer: "Connected" (green dot) and an **Agility CC Discord** link that
      opens discord.gg/agility. No "Copy my tile".
- [ ] Tabs **This mass** / **Previous sessions** switch cleanly, content sits at
      the top, no big gap above the footer.

### D. Laps, fastest laps and previous sessions

- [ ] Run a lap → a lap row appears on the dashboard's Laps tab with tickets;
      the panel's Laps count goes up.
- [ ] Two or more laps → **Fastest lap** (this mass) shows in This session; the
      all-time best under your name updates when beaten and survives a client
      restart.
- [ ] After the mass, **Previous sessions** lists it newest-first: name, date,
      role(s), laps (runners), fastest lap, time on course. Survives a
      restart; another account on the same PC has its own list.

### E. Your own missing-gear check and chat messages

- [ ] As a runner with a required slot empty (defaults: helm, amulet, body,
      legs, boots, plus whatever's ticked in Settings) → one red line "FC gear
      check: No boots" when you join the mass; the panel's Gear row shows it;
      the dashboard Loadout column shows the slot.
- [ ] Weapon rule: a bow with no shield is fine; a crossbow with no shield →
      "No shield"; any other weapon (or none) → "No bow".
- [ ] Chat lines (gear + kit) come **once** when you join the mass and once on
      logging in mid-mass - **not** on every gear swap or world hop. Opening
      a bank repeats them only if something's still missing.
- [ ] Turn off **Pre-mass check messages** in the plugin settings → no gear or
      kit chat lines (panel still shows them).

### F. Kits and the pre-mass check

- [ ] Dashboard Kits: **Create kit** - name at the top, gear per slot,
      inventory lines, nothing saved until **Save kit** (leaving unsaved warns;
      Cancel discards). The info box sits under the title.
- [ ] Untradeable gear can be picked (e.g. Ava's accumulator, fire cape,
      graceful, dragon defender), each name listed once.
- [ ] Gear slot boxes: clicking anywhere in a box (including empty space under
      the arrow) opens it, the cursor is already in its search box so you
      can type straight away, opening one closes any other, and clicking
      anywhere else (or Esc) closes it. Clicking away from the "Items to
      carry" search clears its results.
- [ ] Each slot box only offers items worn in that slot (searching
      "platelegs" in Body finds nothing; in Legs it does). Inventory search
      offers everything.
- [ ] Kit editor layout: gear grid on the left, a game-style 4x7 inventory
      on the right. The coins (150K, padlock) take the first square; nothing
      else stacks, so "Shark x 3" fills three squares. Adding, removing or
      changing a quantity updates it straight away; "N / 28 squares" below.
      You can't add more than fits (27 items besides the coins) - the editor
      says how many squares are left, and saving an overfull kit is refused.
      On a phone the two stack.
- [ ] Every kit has a locked "Coins 150,000 - course entry fee" line.
- [ ] First kit becomes the default; **Mark as default** moves it; **Edit**
      reopens with everything filled; deleting the default hands it on.
- [ ] In-game **Kit** card (runners only): dropdown lists kits with the default
      pre-selected; your choice survives a restart.
- [ ] Attending as a runner away from the course → the Kit card lists exactly
      what's missing ("Wear: …", "Bring: … x2"), and the chat line says the
      same (or "you're all set").
- [ ] Open your bank → missing items are outlined **around the item shape**
      (gold), not a box; take/equip them and the outlines and lines go.
- [ ] Fewer than 150k coins → "Bring: Coins x…" and coins outlined.
- [ ] Any potion dose counts (a (2) for a (4) in the kit); quantity counts
      potions, not doses.
- [ ] As an anti, or once inside the course → no kit check, no outlines.

### G. Gear flags and the observer

- [ ] As a **ranked** member (at/above the Settings threshold) in the course
      during a mass: a red label ("No helm, boots") over runners missing gear,
      and the **Runners in view** card fills in.
- [ ] With RuneLite's Player Indicators showing names above heads, the red
      label sits one line above the name, not on top of it.
- [ ] Runners with full gear, antis and people not attending never get a label.
- [ ] As an **unranked** member (or with the threshold above your rank): no
      labels and no Runners in view card - you still get your own gear line,
      and your scans still reach the dashboard.
- [ ] **Show gear flags** off → labels go. **FC observer** off → other members
      stop being scanned/reported.
- [ ] In a different friends chat, or standing outside the course region →
      nothing scanned, no flags.
- [ ] Change the flag rank on dashboard Settings → takes effect within ~10s.
- [ ] A friend without the plugin is still checked as a runner and appears in
      attendance once seen in the course.

### H. DD calls

- [ ] A ranked account types `dd` in FC chat; one account stands on a DD tile,
      another keeps running; `break dd` → the DD tab shows arrival time, time
      on tile, and flags the one that kept running. Leaderboard updates.
- [ ] Ranked members are left out of DD presence.
- [ ] The panel's This session shows DD timing only while a DD is called.

### I. Attendance and deaths

- [ ] Starting a mass no longer lists the whole FC - only people seen at the
      course, who picked Runner/Anti, or ran a lap. Someone who's only in the
      chat never appears.
- [ ] An anti working the area just outside the course still clocks time.
- [ ] Attendance doesn't time out while you're still at the course; it closes
      ~2 minutes after nobody's seen you.
- [ ] Die in the Wilderness during a mass → the Deaths tab shows name and time
      only (no world / wildy level).

### J. Plugin settings

- [ ] Sections are **Mass** (My role), **FC observer** (FC observer) and
      **Display** (Show sidebar panel, Pre-mass check messages, Show gear
      flags). No Share stats / gear / roster, and no server URL or token
      anywhere.
- [ ] Hovering each setting shows a description that matches what it does.
- [ ] **Show sidebar panel** off → the panel goes away.

### K. What's sent (privacy)

- [ ] Nothing at all until you opt in (only the "is a mass running?" check).
- [ ] No worn items, inventory, skills, combat level, world, wilderness level
      or position for anyone - only empty slot names (e.g. `BOOTS`),
      laps/tickets/lap times, death times, DD timings, and names + FC rank of
      people seen at the mass. (Claude can confirm from a captured request.)
- [ ] The FC member list is never uploaded.

### L. Dashboard accounts

- [ ] **Request one** on the login page → "Request sent"; logging in with it
      says it's waiting for an admin. Bad inputs are refused (short password,
      mismatch, taken name); a 4th sign-up from the same place within an hour
      is refused.
- [ ] Admin → Users: approve as Viewer/Staff/Admin (then it can log in), or
      reject it.
- [ ] Change an account's role; **Remove** an account (it's logged out straight
      away).
- [ ] **Reset password** → temporary password shown once; logging in with it
      lands on Account, every other page sends you back there, the temporary
      password can't be reused as the new one; once changed, everything works.
- [ ] **Account** page: change your own password (wrong current password is
      refused).
- [ ] The **grestinator** row says it can't be changed or removed and has no
      buttons; your own row can't be demoted or removed.
- [ ] Roles: viewer sees Events + DD Leaderboard + Account; staff also Kits and
      can start/stop masses; admin also Settings and Admin.
- [ ] 5 wrong passwords in a row gets rate-limited.

### M. Dashboard pages

- [ ] Events: Start/Stop mass; Attendance tab columns (RSN, Rank, Status, Time
      present, Plugin?, Role, Laps, Loadout); names are plain text.
- [ ] Laps tab leaderboard; Deaths tab (name + time); Manual gap form
      records a missing-gear note for an attendee.
- [ ] DD tab: staff can edit call/break times; DD leaderboard top-3 and CSV
      export work.
- [ ] Settings (admin): cards for Runner gear rules, Gear flags & observer, DD
      calls, DD tiles (read-only); one Save at the bottom; "Settings saved".
      Turning the observer switch off stops plugin scanning within ~10s.
- [ ] No Raggers, Players or Loadouts pages anywhere.
- [ ] Phone-width rendering is usable.
- [ ] **Don't** rotate the plugin token from Admin → Groups on the live server:
      the token is built into the plugin, so it would break every install
      until a new plugin version ships.

### O. Mass over and mass bank alerts

- [ ] **Stop mass** (now asks to confirm) while you're attending and in the
      course → an "Agility mass over" box (same look as the attending
      prompt) and a red chat line within ~10s. OK or × closes it; left alone
      it goes after 2 minutes. Someone attending but outside the course, or
      not attending, gets nothing.
- [ ] **Call mass bank** (Events page next to Stop mass, and on the mass's own
      page; staff+, asks to confirm) → runners attending and in the course get
      "Mass bank starting" + a red chat line within ~10s. Antis, people not
      attending and runners outside the course don't. Walk into the course
      within the minute → you get it then. It disappears when the minute is
      up, or on OK / ×. The page shows when the last bank was called.
- [ ] No Call mass bank button when no mass is running; a viewer never sees it.
- [ ] Both boxes and the attending prompt can be Alt+dragged; clicks on them
      don't reach the game.
- [ ] **Display → Pop-up size** (50-100%, default 100%) shrinks the attending
      prompt and both alerts while open. At 50% every button and the × still
      work where they're drawn, Alt+drag still works, and clicks just outside
      the smaller box reach the game.
- [ ] **Display → Show pop-ups** off: Start mass, Call mass bank and Stop mass
      show no boxes, but the red chat lines still come (the start line now
      says to pick Runner or Anti "in the tracker's sidebar panel"), and the
      panel can still answer. Clicking where a box used to be reaches the game.

### N. Server (checked by Claude)

- [ ] `/docs`, `/redoc`, `/openapi.json` return 404.
- [ ] No per-request log lines (no IPs or RSNs) and logs are size-capped.
- [ ] A spoofed `X-Forwarded-For` from outside doesn't change the rate-limit
      key (only `192.168.1.130` is trusted).
- [ ] `/api/v1/status` and `/api/v1/ingest` return 401 without the token.
- [ ] Item-name worker running (feeds the Kits item search).
- [ ] Nightly 04:00 backup has run.

## 6. Testing without an IDE (beta testers)

```bash
./gradlew shadowJar
java -jar build/libs/agility-cc-tracker-*-all.jar --developer-mode
```

Same one-time Jagex credentials step from §3 applies.
