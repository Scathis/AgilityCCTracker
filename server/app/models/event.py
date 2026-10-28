import datetime as dt
import enum

from sqlalchemy import (
    Boolean,
    DateTime,
    Enum,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
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
    # When staff last called a mass bank during this mass (dashboard button);
    # plugins alert runners in the course for a short while after it.
    mass_bank_called_at: Mapped[dt.datetime | None] = mapped_column(DateTime(), nullable=True)


class Attendance(Base):
    __tablename__ = "attendance"
    __table_args__ = (Index("ix_attendance_event_player", "event_id", "player_id"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    event_id: Mapped[int] = mapped_column(ForeignKey("events.id"), index=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id"), index=True)
    joined_at: Mapped[dt.datetime] = mapped_column(DateTime())
    left_at: Mapped[dt.datetime | None] = mapped_column(DateTime(), nullable=True)
    # FC rank as reported with course sightings (-1 unranked/unknown) - the
    # only use is deciding who sees gear flags (api/v1/status.py).
    rank: Mapped[int] = mapped_column(Integer)
    # A session is opened by a course sighting (the player near the course,
    # or seen in it by another plugin user) and closed by
    # workers/attendance_timeout_worker.py once nobody has seen them for a
    # while; last_sighted_at drives that.
    last_sighted_at: Mapped[dt.datetime] = mapped_column(DateTime())
    # Always true since sightings became the only source (2026-10-27); kept
    # for the attendance-tab filter and older rows.
    sighted_at_course: Mapped[bool] = mapped_column(Boolean, default=False, server_default="0")


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
