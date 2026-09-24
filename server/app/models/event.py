import datetime as dt

from sqlalchemy import DateTime, ForeignKey, Index, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Event(Base):
    __tablename__ = "events"

    id: Mapped[int] = mapped_column(primary_key=True)
    fc_group_id: Mapped[int] = mapped_column(ForeignKey("fc_groups.id"), index=True)
    name: Mapped[str] = mapped_column(String(255))
    started_at: Mapped[dt.datetime] = mapped_column(DateTime())
    ended_at: Mapped[dt.datetime | None] = mapped_column(DateTime(), nullable=True)
    started_by_user_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True)
    notes: Mapped[str | None] = mapped_column(Text(), nullable=True)


class Attendance(Base):
    __tablename__ = "attendance"
    __table_args__ = (Index("ix_attendance_event_player", "event_id", "player_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    event_id: Mapped[int] = mapped_column(ForeignKey("events.id"), index=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id"), index=True)
    joined_at: Mapped[dt.datetime] = mapped_column(DateTime())
    left_at: Mapped[dt.datetime | None] = mapped_column(DateTime(), nullable=True)
    rank: Mapped[int] = mapped_column(Integer)
    world: Mapped[int] = mapped_column(Integer)
