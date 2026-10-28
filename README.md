# Wildy Agility Mass

A RuneLite plugin for the **Agility CC** friends chat's Wilderness Agility
Course masses. It handles attendance, laps, missing-gear checks, kits and DD calls
for the people running the mass, and gives the organisers a live dashboard.

Questions, feedback or bugs: the **[Agility CC Discord](https://discord.gg/agility)**.

---

## For runners

### Getting started

1. Install **Wildy Agility Mass** from the RuneLite Plugin Hub.
2. Join the **Agility Cc** friends chat (Chat-channel tab).
3. When the organisers start a mass, a prompt appears at the top of your game
   screen: **Agility mass started - are you attending?** Pick **Runner** or
   **Anti** (or **Not today**).

That's it - there's nothing to set up. The plugin does nothing at all until
you say you're attending (see [What's shared](#whats-shared-and-when)).

If you close the prompt, the same buttons are in the sidebar panel. If you
forget to answer and just walk into the course during the mass, you're
counted as attending with your saved role, and told so in chat.

When the organisers end the mass, anyone still in the course gets a short
"Agility mass over" box; if they call a **mass bank**, runners in the course
get a "Mass bank starting" box for the next minute. Both can be closed with
OK or ×.

### Runner or Anti

- **Runner** - your laps, time at the course and DD calls are tracked, and
  you're checked for missing gear.
- **Anti** - only your attendance time (and deaths) are recorded. No laps, no
  missing-gear checks, no DD tracking, and nobody's plugin reports on you.

Swapping mid-mass (the **Switch to...** button in the panel) starts a separate
record, so time spent as an anti and as a runner are never mixed together.
Changed your mind? **Not attending any more** in the panel stops tracking you
for that mass (walking back into the course counts you as attending again).

### The sidebar panel

- **Your all-time fastest lap** sits under your name and Agility xp (kept on
  your computer, per account).
- **Agility ring** - fills up to level 99. Past 99 it keeps going: a brighter
  second lap and a fill towards the centre, until it's solid at 200M xp.
- **Friends chat** - a tick if you're in Agility CC, a cross (and how to
  join) if you're not.
- **Mass** - whether one is running and for how long, and your answer.
- **This mass** - your laps and tickets, your fastest lap this mass, whether you're in the course area, any empty required gear
  slots, and DD timing while a DD is called.
- **Kit** - pick one of the organisers' recommended kits. Until you reach the
  course, it checks your gear and inventory against it, tells you what's
  missing, and outlines the missing items in your bank. Every kit includes the
  150,000 coin course entry fee. This check never leaves your computer.
- **Previous sessions** - each mass you've attended: date, role, laps,
  fastest lap and time on the course. Kept on your computer only, per
  account.

### Missing-gear checks

The plugin never reports what anyone is wearing - only whether a required
slot is **empty** (or the weapon isn't a bow/crossbow). Runners are expected to wear something in the slots the organisers choose
(by default helm, amulet, body, legs and boots) and to use a bow or crossbow -
a crossbow also needs something in the shield slot; a bow is two-handed, so
an empty shield is fine. If you're missing something, you get a red chat
line - once when you join the mass (or log in during it), and again when you
open a bank if it's still missing. Never on every gear swap.

Ranked members of the chat (the minimum rank is set by the organisers) also
see a short label over runners in view who are missing gear, so they can help
them sort it out. Members without the plugin - on mobile, for example - still
get checked for missing gear by other people's plugins.

### Settings

Open RuneLite's configuration for **Wildy Agility Mass**:

| Setting | What it does |
|---|---|
| My role | Runner or Anti - remembered for the next mass. |
| FC observer | While you're in the course, report Agility CC members you can see there: name and FC rank (how players without the plugin get attendance), empty required slots, and DD tile time. |
| Show sidebar panel | Show or hide the panel. |
| Pre-mass check messages | The red chat lines about your own gear and kit: once when you join the mass or log in during it, and when you open a bank if something's missing. |
| Show gear flags | The over-head labels on runners missing gear (ranked members only). |
| Show pop-ups | The in-game boxes ("Agility mass started", "Mass bank starting", "Agility mass over"). With it off you still get the red chat lines, and you pick Runner or Anti in the sidebar panel. |
| Pop-up size | Shrinks those boxes (50-100%) for small screens. |

### What's shared, and when

**Before you opt in to a mass, the plugin only asks the server whether a mass
is running** (sending your RSN, so it can remember your answer).

Once you pick Runner or Anti - or walk into the course during a mass - and
only for that mass, it sends to the Agility CC server
(`agilitycc.grestinator.com`, not run or checked by the RuneLite developers):

- that you're at the mass (while you're in or right around the course) and
  your friends chat rank - this is what your attendance time is built from;
- which of your required gear slots are empty (never what you're wearing);
- your Wilderness Agility laps (with tickets and lap time) and the time of
  any deaths;
- for chat members you can see in the course: their name and FC rank, which
  required gear slots they have empty (never what they're actually wearing),
  and how long they were on the DD tiles during a DD call.

It never sends the friends chat member list, anyone's world or position,
worn items, inventory, skills or stats. Your IP address reaches the server
with every request - that's how the internet works - but it's never logged
or stored.

For antis, laps, missing-gear checks and DD calls are ignored, and nobody's plugin
reports on antis or on people who said **Not today**. The server ignores everything from a
player who hasn't opted in to the current mass. Your Agility level in the
panel is read locally and never sent.

---

## For organisers

The dashboard is at the address above. Use **Request one** on the login page
to ask for an account; an admin approves it and chooses what you can do
(Viewer, Staff or Admin).
From it you can:

- start and stop a mass (starting triggers everyone's prompt; stopping tells
  runners still in the course that it's over);
- **Call mass bank** during a mass - runners in the course get an in-game
  alert for the next minute;
- watch attendance (only people who actually turned up), laps, DD calls and
  deaths live, with separate rows for runner and anti time;
- set the runner gear rules, the rank that sees gear flags, and the DD call
  settings (**Settings**);
- build the recommended **Kits** players choose from;
- record a missing-gear note for someone without the plugin.

The observer can be switched off server-side at any time without a plugin
update.

---

## Repository

| Path | What |
|---|---|
| `src/` | The RuneLite plugin (Java 11). |
| `server/` | FastAPI + MySQL API and the web dashboard. |
| `docs/` | Status, build log, API reference, test checklist and Hub submission notes. |

Building the plugin: `./gradlew build`. Server setup and commands are in
[`docs/DEV_SETUP.md`](docs/DEV_SETUP.md) and [`docs/API.md`](docs/API.md).

## Licence

BSD 2-Clause - see [`LICENSE`](LICENSE).
