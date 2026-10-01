import datetime as dt
import enum

from sqlalchemy import JSON, Boolean, DateTime, Enum, ForeignKey, String, UniqueConstraint
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
    __tablename__ = "loadouts"

    id: Mapped[int] = mapped_column(primary_key=True)
    fc_group_id: Mapped[int] = mapped_column(ForeignKey("fc_groups.id"), index=True)
    name: Mapped[str] = mapped_column(String(255))
    context: Mapped[LoadoutContext] = mapped_column(Enum(LoadoutContext, native_enum=True))
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)


class LoadoutRequirement(Base):
    """One slot's rule within a loadout - at most one row per (loadout_id,
    slot), since the interactive "build a runner loadout" character-sheet
    editor works per-slot, not per arbitrary named rule. Semantics (how
    required/allowed_item_ids combine - required-any-item,
    required-from-list, optional-but-restricted-if-worn, or no row at all
    meaning unrestricted) are defined by the plugin's LoadoutEvaluator,
    which is the actual source of truth for evaluation."""

    __tablename__ = "loadout_requirements"
    __table_args__ = (UniqueConstraint("loadout_id", "slot", name="uq_loadout_requirements_slot"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    loadout_id: Mapped[int] = mapped_column(ForeignKey("loadouts.id"), index=True)
    slot: Mapped[str] = mapped_column(String(32))
    required: Mapped[bool] = mapped_column(Boolean, default=False)
    allowed_item_ids: Mapped[list | None] = mapped_column(JSON, nullable=True)


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
