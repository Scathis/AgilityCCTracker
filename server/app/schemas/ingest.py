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


class EquipmentItemIn(BaseModel):
    slot: int
    id: int
    qty: int


class SelfStateIn(BaseModel):
    world: int | None = None
    combat_level: int | None = None
    skills: dict[str, int] | None = None
    equipment: list[EquipmentItemIn] | None = None
    wildy_level: int | None = None
    on_course: bool | None = None
    skulled: bool | None = None
    loadout_failed: list[str] | None = None


class LapEventIn(BaseModel):
    lap_count: int
    completed_at: int
    lap_time_ms: int | None = Field(default=None, ge=MIN_LAP_TIME_MS)
    tickets_gained: int | None = Field(default=None, ge=0, le=MAX_TICKETS_PER_LAP)


class DeathEventIn(BaseModel):
    died_at: int
    world: int
    wildy_level: int


class RosterMemberIn(BaseModel):
    name: str = Field(max_length=RSN_MAX_LENGTH)
    rank: int
    world: int


class RosterSnapshotIn(BaseModel):
    ts: int
    members: list[RosterMemberIn] = Field(max_length=600)


class LoadoutCheckIn(BaseModel):
    rsn: Rsn
    loadout_id: int
    failed: list[str]
    ts: int


class DdPresenceIn(BaseModel):
    rsn: Rsn
    arrived_after_ms: int | None = None
    ms_on_tile: int
    left_before_break: bool
    seen_running: bool
    observed_ms: int
    dd_failed: list[str]


class DdSectionIn(BaseModel):
    called_at: int
    broken_at: int | None = None
    presence: list[DdPresenceIn] = Field(default_factory=list, max_length=300)


class IngestBatchIn(BaseModel):
    rsn: Rsn
    self_state: SelfStateIn | None = Field(default=None, alias="self")
    laps: list[LapEventIn] = Field(default_factory=list, max_length=200)
    deaths: list[DeathEventIn] = Field(default_factory=list, max_length=50)
    roster: RosterSnapshotIn | None = None
    loadout_checks: list[LoadoutCheckIn] = Field(default_factory=list, max_length=300)
    dd: DdSectionIn | None = None
    # RSNs actually sighted at the course this upload cycle (self + anyone
    # observed in-course) - sent every cycle regardless of whether anything
    # else changed, unlike the rest of this payload. Keeps attendance
    # sessions alive; see _process_course_sightings() and
    # workers/attendance_timeout_worker.py.
    course_sightings: list[Rsn] = Field(default_factory=list, max_length=200)
