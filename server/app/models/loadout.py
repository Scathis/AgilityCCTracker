import datetime as dt
import enum

from sqlalchemy import JSON, Boolean, DateTime, Enum, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class LoadoutContext(enum.StrEnum):
    RUNNING = "running"
    DD = "dd"


class LoadoutCheckSource(enum.StrEnum):
    SELF = "self"
    FC_SCAN = "fc_scan"
    STAFF_MANUAL = "staff_manual"


class Loadout(Base):
    """Since 2026-10-27 only an internal anchor row ("Running") that
    loadout_checks hang off - the rules themselves are the group's gear rules
    (FcGroup.required_slots / require_ranged_weapon), and recommended setups
    are Kits. Kept so existing gear-check history keeps its foreign key."""

    __tablename__ = "loadouts"

    id: Mapped[int] = mapped_column(primary_key=True)
    fc_group_id: Mapped[int] = mapped_column(ForeignKey("fc_groups.id"), index=True)
    name: Mapped[str] = mapped_column(String(255))
    context: Mapped[LoadoutContext] = mapped_column(Enum(LoadoutContext, native_enum=True))
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)


class LoadoutCheck(Base):
    __tablename__ = "loadout_checks"

    id: Mapped[int] = mapped_column(primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id"), index=True)
    event_id: Mapped[int] = mapped_column(ForeignKey("events.id"), index=True)
    loadout_id: Mapped[int] = mapped_column(ForeignKey("loadouts.id"))
    checked_at: Mapped[dt.datetime] = mapped_column(DateTime())
    failed_requirement_keys: Mapped[list] = mapped_column(JSON)
    source: Mapped[LoadoutCheckSource] = mapped_column(Enum(LoadoutCheckSource, native_enum=True))
    reported_by_player_id: Mapped[int | None] = mapped_column(
        ForeignKey("players.id"), nullable=True
    )
    reported_by_user_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)
    notes: Mapped[str | None] = mapped_column(String(255), nullable=True)
