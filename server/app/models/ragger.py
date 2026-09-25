import datetime as dt
import enum

from sqlalchemy import JSON, DateTime, Enum, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class RaggerStatus(enum.StrEnum):
    ACTIVE = "active"
    CLEARED = "cleared"


class Ragger(Base):
    __tablename__ = "raggers"

    id: Mapped[int] = mapped_column(primary_key=True)
    rsn: Mapped[str] = mapped_column(String(12), unique=True)
    status: Mapped[RaggerStatus] = mapped_column(
        Enum(RaggerStatus, native_enum=True), default=RaggerStatus.ACTIVE
    )
    threat_notes: Mapped[str | None] = mapped_column(Text(), nullable=True)
    added_by_user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[dt.datetime] = mapped_column(DateTime())
    combat_level: Mapped[int | None] = mapped_column(Integer, nullable=True)
    skills: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    hiscores_updated_at: Mapped[dt.datetime | None] = mapped_column(DateTime(), nullable=True)


class RaggerSighting(Base):
    __tablename__ = "ragger_sightings"

    id: Mapped[int] = mapped_column(primary_key=True)
    ragger_id: Mapped[int] = mapped_column(ForeignKey("raggers.id"), index=True)
    event_id: Mapped[int | None] = mapped_column(ForeignKey("events.id"), nullable=True)
    seen_at: Mapped[dt.datetime] = mapped_column(DateTime())
    world: Mapped[int | None] = mapped_column(Integer, nullable=True)
    gear_notes: Mapped[str | None] = mapped_column(Text(), nullable=True)
    reported_by_user_id: Mapped[int] = mapped_column(ForeignKey("users.id"))
