# Wildy Agility FC Tracker

A RuneLite plugin for the **Agility CC** friends chat's Wilderness Agility
Course masses. It handles attendance, laps, missing-gear checks, kits and DD calls
for the people running the mass, and gives the organisers a live dashboard.

Questions, feedback or bugs: the **[Agility CC Discord](https://discord.gg/agility)**.

---

## For runners

### Getting started

1. Install **Wildy Agility FC Tracker** from the RuneLite Plugin Hub.
2. Join the **Agility Cc** friends chat (Chat-channel tab).
3. When the organisers start a mass, a prompt appears at the top of your game
   screen: **Agility mass started - are you attending?** Pick **Runner** or
   **Anti** (or **Not today**).

That's it - there's nothing to set up. The plugin does nothing at all until
you say you're attending (see [What's shared](#whats-shared-and-when)).

If you close the prompt, the same buttons are in the sidebar panel. If you
forget to answer and just walk into the course during the mass, you're
counted as attending with your saved role, and told so in chat.

### Runner or Anti

- **Runner** - your laps, time at the course and DD calls are tracked, and
  you're checked for missing gear.
- **Anti** - only your attendance time (and deaths) are recorded. No laps, no
  missing-gear checks, no DD tracking, and nobody's plugin reports on you.

Swapping mid-mass (the **Switch to...** button in the panel) starts a separate
record, so time spent as an anti and as a runner are never mixed together.

### The sidebar panel

- **Agility ring** - fills up to level 99. Past 99 it keeps going: a brighter
  second lap and a fill towards the centre, until it's solid at 200M xp.
- **Friends chat** - a tick if you're in Agility CC, a cross (and how to
  join) if you're not.
- **Mass** - whether one is running and for how long, and your answer.
- **This mass** - your laps and tickets, whether you're in the course area,
  any empty required gear slots, and DD timing while a DD is called.
- **Kit** - pick one of the organisers' recommended kits. Until you reach the
  course, it checks your gear and inventory against it, tells you what's
  missing, and outlines the missing items in your bank. Every kit includes the
  150,000 coin course entry fee. This check never leaves your computer.
- **Previous sessions** - each mass you've attended: date, role, laps and time
  on the course. Kept on your computer only, per account.

### Missing-gear checks

The plugin never reports what anyone is wearing - only whether a required
slot is **empty** (or the weapon isn't a bow/crossbow). Runners are expected to wear something in the slots the organisers choose
(by default helm, amulet, body, legs and boots) and to use a bow or crossbow -
a crossbow also needs something in the shield slot; a bow is two-handed, so
an empty shield is fine. If you're missing something, you get a red chat
line.

Ranked members of the chat (the minimum rank is set by the organisers) also
see a short label over runners in view who are missing gear, so they can help
them sort it out. Members without the plugin - on mobile, for example - still
get checked for missing gear by other people's plugins.

### Settings

Open RuneLite's configuration for **Wildy Agility FC Tracker**:

| Setting | What it does |
|---|---|
| My role | Runner or Anti - remembered for the next mass. |
| Share roster | Send the friends chat member list while a mass runs (how the dashboard knows who's in the chat). |
| FC observer | Report which required slots chat members you can see at the course have empty, and their DD presence. |
| Show sidebar panel | Show or hide the panel. |
| Gear chat warnings | The red chat line when one of your own required slots is empty. |
| Show gear flags | The over-head labels on runners missing gear (ranked members only). |

### What's shared, and when

**Before you opt in to a mass, the plugin only asks the server whether a mass
is running** (sending your RSN, so it can remember your answer).

Once you pick Runner or Anti - or walk into the course during a mass - and
only for that mass, it sends to the Agility CC server
(`agilitycc.grestinator.com`, not run or checked by the RuneLite developers):

- your RSN and world, and which of your required gear slots are empty
  (never what you're wearing);
- your Wilderness Agility laps (with tickets gained) and deaths;
- the names, ranks and worlds of the friends chat's members;
- for chat members seen at the course: their name, which required gear slots
  they have empty (never what they're actually wearing), and how long they
  were on the DD tiles during a DD call.

For antis, laps, missing-gear checks and DD calls are ignored, and nobody's plugin
reports on antis or on people who said **Not today**. The server ignores everything from a
player who hasn't opted in to the current mass. Nothing about other players'
locations or items ever leaves your client - and neither do your own worn
items, inventory, skills or stats. (Your Agility level in the panel is read
locally and never sent.)

---

## For organisers

The dashboard is at the address above; ask in the Discord for an account.
From it you can:

- start and stop a mass (this is what triggers everyone's prompt);
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
