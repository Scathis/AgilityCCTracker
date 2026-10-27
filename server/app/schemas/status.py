from pydantic import BaseModel


class ActiveEventOut(BaseModel):
    id: int
    name: str
    started_at: int


class ParticipantOut(BaseModel):
    rsn: str
    # Uppercase on the wire ("ATTENDING"/"DECLINED", "RUNNER"/"ANTI") for the
    # same reason as LoadoutOut.context: Gson maps an enum by constant name.
    status: str
    role: str


class RequirementOut(BaseModel):
    slot: str
    required: bool
    allowed_item_ids: list[int]


class LoadoutOut(BaseModel):
    id: int
    name: str
    # Uppercase on the wire ("RUNNING"/"DD") to match the plugin's Java enum
    # (Gson serialises an enum by its exact constant name) - the DB stores
    # lowercase per the §6.2 schema's own ENUM('running','dd'). The mapping
    # from DB to wire happens once, in services/loadouts.py's serializer -
    # never inline at the route, so it can't silently drift.
    context: str
    requirements: list[RequirementOut]


class DdTileOut(BaseModel):
    x: int
    y: int
    plane: int


class DdConfigOut(BaseModel):
    tiles: list[DdTileOut]
    call_regex: str
    break_regex: str
    min_rank: int
    arrive_grace_s: int
    leave_grace_s: int
    max_minutes: int
    crossbow_counts_as_bow_at_dd: bool


class StatusOut(BaseModel):
    fc_owner: str
    fc_name: str
    server_time: int
    observer_enabled: bool
    active_event: ActiveEventOut | None
    # Plugin users who answered the active mass's "attending?" prompt. A
    # roster member who's absent here is a non-plugin user, treated as a runner.
    participants: list[ParticipantOut]
    loadouts: list[LoadoutOut]
    dd: DdConfigOut
