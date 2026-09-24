import datetime as dt

from sqlalchemy import JSON, Boolean, DateTime, ForeignKey, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class DdCall(Base):
    __tablename__ = "dd_calls"

    id: Mapped[int] = mapped_column(primary_key=True)
    event_id: Mapped[int] = mapped_column(ForeignKey("events.id"), index=True)
    called_at: Mapped[dt.datetime] = mapped_column(DateTime())
    broken_at: Mapped[dt.datetime | None] = mapped_column(DateTime(), nullable=True)
    reports: Mapped[int] = mapped_column(Integer, default=0)
    edited_by_user_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)


class DdPresence(Base):
    __tablename__ = "dd_presence"
    __table_args__ = (
        UniqueConstraint("dd_call_id", "player_id", name="uq_dd_presence_call_player"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    dd_call_id: Mapped[int] = mapped_column(ForeignKey("dd_calls.id"), index=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id"), index=True)
    arrived_after_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    ms_on_tile: Mapped[int] = mapped_column(Integer, default=0)
    left_before_break: Mapped[bool] = mapped_column(Boolean, default=False)
    seen_running: Mapped[bool] = mapped_column(Boolean, default=False)
    laps_during_dd: Mapped[int] = mapped_column(Integer, default=0)
    observed_ms: Mapped[int] = mapped_column(Integer, default=0)
    dd_loadout_failed: Mapped[list] = mapped_column(JSON, default=list)
    sources: Mapped[list] = mapped_column(JSON, default=list)
