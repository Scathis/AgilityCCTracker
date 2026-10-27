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
- **List size limits** (rejected if exceeded): roster ≤ 600 members, laps ≤
  200, loadout checks ≤ 300, DD presence entries ≤ 300.

## `GET /healthz`

No auth. `{"ok": true}`. Used for container healthchecks and uptime checks.

## `GET /api/v1/status`

Returns the current config for the token's FC group — the plugin polls this
every 60s (and once on startup / config change) and caches it.

```json
{
  "fc_owner": "Owner Name",
  "fc_name": "Wildy Agility",
  "server_time": 1727190000000,
  "observer_enabled": true,
  "active_event": {"id": 12, "name": "Tuesday mass", "started_at": 1727190000000},
  "loadouts": [
    {
      "id": 1,
      "name": "Running",
      "context": "RUNNING",
      "requirements": [
        {"slot": "WEAPON", "required": true, "allowed_item_ids": [841, 839]}
      ]
    }
  ],
  "dd": {
    "tiles": [{"x": 0, "y": 0, "plane": 0}, {"x": 0, "y": 0, "plane": 0}],
    "call_regex": "^\\s*dd\\s*!*$",
    "break_regex": "^\\s*(break|brk)\\s*(dd)?\\s*!*$",
    "min_rank": 3,
    "arrive_grace_s": 15,
    "leave_grace_s": 5,
    "max_minutes": 10,
    "crossbow_counts_as_bow_at_dd": true
  }
}
```

`active_event` is `null` when no event is running. Regexes are matched
case-insensitively.

## `POST /api/v1/ingest`

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
    "world": 330,
    "combat_level": 98,
    "skills": {"attack": 13034431},
    "equipment": [{"slot": 3, "id": 861, "qty": 1}],
    "wildy_level": 52,
    "on_course": true,
    "skulled": false,
    "loadout_failed": ["HEAD"]
  },
  "laps": [{"lap_count": 1234, "completed_at": 1727190000000, "lap_time_ms": 61200, "tickets_gained": 1}],
  "deaths": [{"died_at": 1727190000000, "world": 330, "wildy_level": 52}],
  "roster": {"ts": 1727190000000, "members": [{"name": "A", "rank": 0, "world": 330}]},
  "loadout_checks": [{"rsn": "B", "loadout_id": 1, "failed": ["WEAPON"], "ts": 1727190000000}],
  "course_sightings": ["Player Name", "B"],
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
        "observed_ms": 60000,
        "dd_failed": []
      }
    ]
  }
}
```

**Note what's never in here:** no coordinates or item ids for anyone other
than the reporter themselves. Other players only ever show up as RSN + rank
+ world (roster), or as loadout *requirement keys* they failed (never which
items they were or weren't wearing), or as DD presence timing/booleans. This
is a hard boundary enforced in the plugin, not just a server-side filter —
see `BUILD_PLAN.md` §1.1–1.2 and `scan/FcMemberScanner`.

### Processing (one DB transaction per request)

1. Upsert the reporter's `players` row by RSN; mark `is_plugin_user = true`,
   bump `last_seen`.
2. `self`: stored as a new `player_snapshots` row only if it changed since
   the reporter's last plugin snapshot. `loadout_failed` is stored as a
   `loadout_checks` row (`source='self'`) only if changed and an event is
   currently active.
3. `laps`: inserted idempotently (`UNIQUE(player_id, lap_count)` — a
   duplicate POST is a no-op). Assigned to whichever event's time window
   contains `completed_at`, if any.
4. `deaths`: same idempotency pattern.
5. `roster` (only while an event is active): diffed against currently-open
   attendance sessions for that event. A member missing from a newer
   snapshot closes their session — but only if the reporter is on the same
   world as that session, so one reporter world-hopping doesn't
   incorrectly close everyone else's attendance.
6. `loadout_checks` from FC-scan observations: only stored if an event is
   active, `observer_enabled` is true, and the named RSN has an attendance
   row in this event — and only if it differs from the last stored result
   for that player/event/loadout.
7. `dd`: matched to an existing `dd_calls` row within ±10s of `called_at`,
   or a new one is created. Presence rows are merged per-player: max of
   `ms_on_tile`/`observed_ms`, min of `arrived_after_ms`, booleans OR'd,
   `dd_loadout_failed` unioned, `sources` tracks which reporters
   contributed.
8. After a DD call closes: `laps_during_dd` is computed from laps whose
   `completed_at` falls inside `[called_at, broken_at]`; any lap during
   that window also sets `seen_running = true`.

## Errors

Standard FastAPI/Pydantic validation errors (`422`) for malformed payloads,
`401` for a missing/invalid token, `403` if the group is otherwise not
permitted, `429` for the auth rate limit, `413` for an oversized body.
