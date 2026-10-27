from typing import Annotated

from pydantic import BaseModel, Field

# players.rsn is VARCHAR(12) - bounding here turns an oversized name into a
# clean 422 instead of an unhandled MySQL truncation error (a 500).
RSN_MAX_LENGTH = 12

# The Wilderness Agility Course's own wiki page states a theoretical
# fastest-possible lap of 67 ticks (~40.2s at 0.6s/tick) under perfect play.
# A generous floor well below that catches a naively-forked plugin
# hardcoding an absurd lap_time_ms without ever rejecting a real one - not a
# full anti-cheat (a batch of fabricated laps with plausible-looking
# completed_at spacing would still get through; that needs server-side game
# verification, out of scope for a "catch the laziest cheats for free" fix).
MIN_LAP_TIME_MS = 30_000

# Exactly one ticket per lap, always (confirmed on the wiki - no variance,
# no maximum) - 0 allowed defensively in case a lap is ever reported before
# the ticket count settles, but nothing else is physically possible.
MAX_TICKETS_PER_LAP = 1

Rsn = Annotated[str, Field(max_length=RSN_MAX_LENGTH)]


class SelfStateIn(BaseModel):
    # Only which of the reporter's required slots are empty. Never skills,
    # gear, world or location - older plugin builds still send world/skills/
    # combat_level/equipment/wildy_level/skulled/on_course; pydantic drops
    # unknown fields, so they're ignored.
    loadout_failed: list[str] | None = None


class LapEventIn(BaseModel):
    lap_count: int
    completed_at: int
    lap_time_ms: int | None = Field(default=None, ge=MIN_LAP_TIME_MS)
    tickets_gained: int | None = Field(default=None, ge=0, le=MAX_TICKETS_PER_LAP)


class DeathEventIn(BaseModel):
    # Time only - every death that matters happens at the course. Older
    # builds' world/wildy_level are ignored.
    died_at: int


class CourseSightingIn(BaseModel):
    name: Rsn
    # FC rank (RuneLite FriendsChatRank value, -1 unranked/unknown) - used
    # only to decide who sees gear flags.
    rank: int = -1


class LoadoutCheckIn(BaseModel):
    rsn: Rsn
    # Ignored since 2026-10-27 (checks are always stored against the group's
    # Running anchor); still accepted so older plugin builds don't 422.
    loadout_id: int | None = None
    failed: list[str]
    ts: int


class DdPresenceIn(BaseModel):
    rsn: Rsn
    arrived_after_ms: int | None = None
    ms_on_tile: int
    left_before_break: bool
    seen_running: bool
    observed_ms: int


class DdSectionIn(BaseModel):
    called_at: int
    broken_at: int | None = None
    presence: list[DdPresenceIn] = Field(default_factory=list, max_length=300)


class IngestBatchIn(BaseModel):
    rsn: Rsn
    self_state: SelfStateIn | None = Field(default=None, alias="self")
    laps: list[LapEventIn] = Field(default_factory=list, max_length=200)
    deaths: list[DeathEventIn] = Field(default_factory=list, max_length=50)
    loadout_checks: list[LoadoutCheckIn] = Field(default_factory=list, max_length=300)
    dd: DdSectionIn | None = None
    # Who is at the mass this upload cycle (the reporter, when near the
    # course, plus FC members they can see in it), with FC rank - sent every
    # cycle. Attendance is built from this alone; see
    # _process_course_sightings() and workers/attendance_timeout_worker.py.
    # Older builds send bare names (rank unknown). The FC member list
    # ("roster") they also send is ignored.
    course_sightings: list[CourseSightingIn | Rsn] = Field(default_factory=list, max_length=200)


class RespondIn(BaseModel):
    rsn: Rsn
    event_id: int
    attending: bool
    role: str = "RUNNER"
