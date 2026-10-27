# Plugin API (`/api/v1`)

This is the API the RuneLite plugin talks to. It's not a general-purpose
public API — there's exactly one client (this project's plugin) and one
token per FC group.

## Auth

Every endpoint except `GET /healthz` requires:

```
Authorization: Bearer <token>
```

The token is 32 random url-safe bytes, generated once by
`create-group`/`rotate-token` and shown exactly once — the server only ever
stores `HMAC-SHA256(APP_SECRET, token)`, never the raw token, so it can't be
recovered later, only rotated.

- **Rate limit:** 5 failed-auth attempts per IP per 5 minutes → `429`.
- **Body size limit:** ~100KB per request → `413` if exceeded.
- **List size limits** (rejected if exceeded): course sightings ≤ 200, laps ≤
  200, loadout checks ≤ 300, DD presence entries ≤ 300.

## `GET /healthz`

No auth. `{"ok": true}`. Used for container healthchecks and uptime checks.

## `GET /api/v1/status`

Returns the current config for the token's FC group — the plugin polls this
every 10s (and once on startup / login) and caches it.

```json
{
  "fc_owner": "Owner Name",
  "fc_name": "Wildy Agility",
  "server_time": 1727190000000,
  "observer_enabled": true,
  "flag_viewer_min_rank": 0,
  "active_event": {"id": 12, "name": "Tuesday mass", "started_at": 1727190000000},
  "participants": [{"rsn": "A", "status": "ATTENDING", "role": "RUNNER"}],
  "gear_rules": {"required_slots": ["HEAD", "AMULET", "TORSO", "LEGS", "BOOTS"], "ranged_weapon": true},
  "kits": [
    {
      "id": 1,
      "name": "Standard runner",
      "is_default": true,
      "equipment": [{"slot": "WEAPON", "item_id": 861}],
      "inventory": [{"item_id": 12625, "qty": 2}]
    }
  ],
  "dd": {
    "tiles": [{"x": 0, "y": 0, "plane": 0}, {"x": 0, "y": 0, "plane": 0}],
    "call_regex": "^\\s*dd\\s*!*$",
    "break_regex": "^\\s*(break|brk)\\s*(dd)?\\s*!*$",
    "min_rank": 3,
    "arrive_grace_s": 15,
    "leave_grace_s": 5,
    "max_minutes": 10
  }
}
```

`active_event` is `null` when no event is running. Regexes are matched
case-insensitively.

`gear_rules` (set on the dashboard Kits page) is what a runner is flagged
against: every `required_slots` slot must have something worn, and with
`ranged_weapon` the weapon must be a bow or crossbow, a crossbow also needing
a shield. The plugin evaluates it; failed slot names (e.g. `"BOOTS"`,
`"WEAPON"`, `"SHIELD"`) are what come back in `loadout_failed` /
`loadout_checks`. `kits` are recommended setups for the plugin's local
pre-course check and bank highlighting - nothing about a player's own
inventory is ever sent back. The DD gear (bow) check was dropped on
2026-10-27; `dd_failed` is accepted and ignored.

## `POST /api/v1/event/respond`

A plugin user's answer to the "mass started - attending?" popup.

```json
{"rsn": "Player Name", "event_id": 7, "attending": true, "role": "RUNNER"}
```

`role` is `RUNNER` or `ANTI` (case-insensitive). Idempotent upsert; answering
again while the mass is active changes the role. `409` if `event_id` isn't
the FC's currently active mass, `422` for a bad role.

`GET /status?rsn=<viewer>` (the plugin passes the local player's name) also returns `flag_viewer_min_rank`: the lowest FC rank (`-1` unranked, `0` friend, `1` recruit, …) that the plugin shows other players' gear gaps to, set on the dashboard Settings page.

`/status` lists the answers for the active mass as `participants`, but **only to an entitled viewer** — one whose currently-open attendance row (rank taken from course sightings reported by plugin users, not a field the asker sets) is at or above `flag_viewer_min_rank`. Everyone else gets just their own entry (enough to restore their own answer). The `rsn` is self-reported, so this stops the stock plugin, not a modified client
(`[{"rsn", "status": "ATTENDING"|"DECLINED", "role": "RUNNER"|"ANTI"}]`).
Someone not listed has no plugin answer (e.g. mobile) and is treated as a
runner by observers.

## `POST /api/v1/ingest`

**Opt-in gate.** The whole batch is dropped (`200`, `{"ignored": true}`,
nothing stored, no player row created) unless the reporter has an
`ATTENDING` answer for the currently active mass. For `ANTI` reporters the
server also drops `laps`, `dd` and `self.loadout_failed` (attendance time,
sightings and deaths are still kept). Observer-reported
`loadout_checks` and DD presence about an anti, or about a plugin user who
declined, are dropped too.

The plugin sends one of these every 2 seconds (fields omitted when nothing
in that section has changed — if only `rsn` would remain, the plugin skips
sending entirely). `course_sightings` is the one exception: it's sent on
*every* cycle regardless of whether anything else changed, since it's a
live heartbeat (self + any FC members currently observed in the course
area) that keeps attendance sessions from auto-timing out — see
`workers/attendance_timeout_worker.py`.

Note what's deliberately **not** here: no GP value of any kind (gear
value, risk value) is ever collected or sent, for self or anyone else —
removed entirely as a Plugin Hub approval risk (a tool estimating how
much GP someone is carrying reads as PK-target reconnaissance regardless
of intent).

```json
{
  "rsn": "Player Name",
  "self": {
    "loadout_failed": ["HEAD"]
  },
  "laps": [{"lap_count": 1234, "completed_at": 1727190000000, "lap_time_ms": 61200, "tickets_gained": 1}],
  "deaths": [{"died_at": 1727190000000}],
  "loadout_checks": [{"rsn": "B", "failed": ["WEAPON"], "ts": 1727190000000}],
  "course_sightings": [{"name": "Player Name", "rank": -1}, {"name": "B", "rank": 2}],
  "dd": {
    "called_at": 1727190000000,
    "broken_at": null,
    "presence": [
      {
        "rsn": "B",
        "arrived_after_ms": 4200,
        "ms_on_tile": 42000,
        "left_before_break": false,
        "seen_running": false,
        "observed_ms": 60000
      }
    ]
  }
}
```

**Note what's never in here:** no coordinates, worlds, item ids, inventory or
stats - for anyone, the reporter included. Other players only ever show up as
RSN + FC rank when seen at the course, as the *slot keys* they have empty
(never what they're wearing), or as DD presence timing/booleans. The FC
member list is not sent (older builds' `roster`, `self.world` and death
`world`/`wildy_level` are accepted and ignored). This
is a hard boundary enforced in the plugin, not just a server-side filter —
see `BUILD_PLAN.md` §1.1–1.2 and `scan/FcMemberScanner`.

### Processing (one DB transaction per request)

0. Opt-in gate (above).
1. Upsert the reporter's `players` row by RSN; mark `is_plugin_user = true`,
   bump `last_seen`.
2. `self.loadout_failed` is stored as a `loadout_checks` row
   (`source='self'`) only if changed and an event is currently active.
3. `laps`: inserted idempotently (`UNIQUE(player_id, lap_count)` — a
   duplicate POST is a no-op). Assigned to whichever event's time window
   contains `completed_at`, if any.
4. `deaths`: same idempotency pattern (time only).
5. `course_sightings`: the reporter (sent while they're in or right around
   the course) and FC members they see in it. Each keeps that player's open
   attendance session alive, or opens one (rank = highest reported); the
   timeout worker closes sessions nobody has sighted for 2 minutes. This is
   the only source of attendance.
6. `loadout_checks` from FC-scan observations: only stored if an event is
   active, `observer_enabled` is true, and the named RSN has an attendance
   row in this event — and only if it differs from the last stored result
   for that player/event/loadout.
7. `dd`: matched to an existing `dd_calls` row within ±10s of `called_at`,
   or a new one is created. Presence rows are merged per-player: max of
   `ms_on_tile`/`observed_ms`, min of `arrived_after_ms`, booleans OR'd,
   `sources` tracks which reporters contributed.
8. After a DD call closes: `laps_during_dd` is computed from laps whose
   `completed_at` falls inside `[called_at, broken_at]`; any lap during
   that window also sets `seen_running = true`.

## Errors

Standard FastAPI/Pydantic validation errors (`422`) for malformed payloads,
`401` for a missing/invalid token, `403` if the group is otherwise not
permitted, `429` for the auth rate limit, `413` for an oversized body.
