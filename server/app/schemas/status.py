from pydantic import BaseModel


class ActiveEventOut(BaseModel):
    id: int
    name: str
    started_at: int
    # Epoch ms of the latest mass bank call during this mass, or null. The
    # plugin compares it with server_time to alert runners for a short window.
    mass_bank_at: int | None = None


class ParticipantOut(BaseModel):
    rsn: str
    # Uppercase on the wire ("ATTENDING"/"DECLINED", "RUNNER"/"ANTI") for the
    # same reason as the plugin's other enums: Gson maps an enum by constant name.
    status: str
    role: str


class GearRulesOut(BaseModel):
    # Slot names match the plugin's Slot enum (HEAD, AMULET, TORSO, ...).
    required_slots: list[str]
    ranged_weapon: bool


class KitEquipmentOut(BaseModel):
    slot: str
    item_id: int


class KitInventoryOut(BaseModel):
    item_id: int
    qty: int


class KitOut(BaseModel):
    id: int
    name: str
    is_default: bool
    equipment: list[KitEquipmentOut]
    inventory: list[KitInventoryOut]


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


class StatusOut(BaseModel):
    fc_owner: str
    fc_name: str
    server_time: int
    observer_enabled: bool
    # Lowest FC rank the plugin shows other players' gear gaps to (see FcGroup).
    flag_viewer_min_rank: int
    active_event: ActiveEventOut | None
    # Plugin users who answered the active mass's "attending?" prompt. A
    # FC member who's absent here is a non-plugin user, treated as a runner.
    participants: list[ParticipantOut]
    gear_rules: GearRulesOut
    kits: list[KitOut]
    dd: DdConfigOut
