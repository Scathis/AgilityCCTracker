import datetime as dt

from sqlalchemy import DateTime, ForeignKey, Integer, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Lap(Base):
    __tablename__ = "laps"
    __table_args__ = (UniqueConstraint("player_id", "lap_count", name="uq_laps_player_lapcount"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id"), index=True)
    event_id: Mapped[int | None] = mapped_column(ForeignKey("events.id"), nullable=True)
    lap_count: Mapped[int] = mapped_column(Integer)
    completed_at: Mapped[dt.datetime] = mapped_column(DateTime())
    lap_time_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
    tickets_gained: Mapped[int | None] = mapped_column(Integer, nullable=True)
