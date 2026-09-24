from pydantic import BaseModel, Field


class EquipmentItemIn(BaseModel):
    slot: int
    id: int
    qty: int


class SelfStateIn(BaseModel):
    world: int | None = None
    combat_level: int | None = None
    skills: dict[str, int] | None = None
    equipment: list[EquipmentItemIn] | None = None
    gear_value: int | None = None
    risk_value: int | None = None
    wildy_level: int | None = None
    on_course: bool | None = None
    skulled: bool | None = None
    loadout_failed: list[str] | None = None


class LapEventIn(BaseModel):
    lap_count: int
    completed_at: int
    lap_time_ms: int | None = None
    tickets_gained: int | None = None


class DeathEventIn(BaseModel):
    died_at: int
    world: int
    wildy_level: int


class RosterMemberIn(BaseModel):
    name: str
    rank: int
    world: int


class RosterSnapshotIn(BaseModel):
    ts: int
    members: list[RosterMemberIn] = Field(max_length=600)


class LoadoutCheckIn(BaseModel):
    rsn: str
    loadout_id: int
    failed: list[str]
    ts: int


class DdPresenceIn(BaseModel):
    rsn: str
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
    rsn: str
    account_hash: str | None = None
    self_state: SelfStateIn | None = Field(default=None, alias="self")
    laps: list[LapEventIn] = Field(default_factory=list, max_length=200)
    deaths: list[DeathEventIn] = Field(default_factory=list, max_length=50)
    roster: RosterSnapshotIn | None = None
    loadout_checks: list[LoadoutCheckIn] = Field(default_factory=list, max_length=300)
    dd: DdSectionIn | None = None
