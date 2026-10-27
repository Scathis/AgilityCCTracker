import datetime as dt
import enum

from sqlalchemy import DateTime, Enum, ForeignKey, Index, Integer, String, Text, UniqueConstraint
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
    # Last time this player was actually sighted at the course (a lap, a
    # self-reported on_course ping, or being observed in-course by another
    # plugin user) - distinct from joined_at/left_at, which track FC-channel
    # membership. Drives the auto-timeout close in workers/attendance_timeout_worker.py.
    last_sighted_at: Mapped[dt.datetime] = mapped_column(DateTime())


class ParticipantStatus(enum.StrEnum):
    ATTENDING = "attending"
    DECLINED = "declined"


class ParticipantRole(enum.StrEnum):
    RUNNER = "runner"
    ANTI = "anti"


class EventParticipant(Base):
    """A plugin user's own answer to "mass started - attending?" for one
    event. Absence of a row means the player never answered (or has no
    plugin at all), which for FC members the plugin observes is treated as
    an ordinary runner. Ingest from a reporter without an ATTENDING row for
    the active event is ignored entirely - see api/v1/ingest.py."""

    __tablename__ = "event_participants"
    __table_args__ = (UniqueConstraint("event_id", "player_id", name="uq_event_participant"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    event_id: Mapped[int] = mapped_column(ForeignKey("events.id"), index=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id"), index=True)
    status: Mapped[ParticipantStatus] = mapped_column(Enum(ParticipantStatus, native_enum=True))
    role: Mapped[ParticipantRole] = mapped_column(Enum(ParticipantRole, native_enum=True))
    responded_at: Mapped[dt.datetime] = mapped_column(DateTime())


class ParticipantStint(Base):
    """One continuous stretch of a player attending a mass in a single role.
    Switching Runner/Anti mid-mass closes the open stint and opens a new one,
    so each role's time and laps are reported separately instead of combined.
    A NULL ended_at means "still going" (or until the mass ended)."""

    __tablename__ = "participant_stints"

    id: Mapped[int] = mapped_column(primary_key=True)
    event_id: Mapped[int] = mapped_column(ForeignKey("events.id"), index=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id"), index=True)
    role: Mapped[ParticipantRole] = mapped_column(Enum(ParticipantRole, native_enum=True))
    started_at: Mapped[dt.datetime] = mapped_column(DateTime())
    ended_at: Mapped[dt.datetime | None] = mapped_column(DateTime(), nullable=True)
