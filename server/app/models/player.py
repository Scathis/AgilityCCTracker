import datetime as dt
import enum

from sqlalchemy import JSON, Boolean, DateTime, Enum, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class SnapshotSource(enum.StrEnum):
    PLUGIN = "plugin"
    HISCORES = "hiscores"


class Player(Base):
    __tablename__ = "players"

    id: Mapped[int] = mapped_column(primary_key=True)
    rsn: Mapped[str] = mapped_column(String(12), unique=True)
    first_seen: Mapped[dt.datetime] = mapped_column(DateTime())
    last_seen: Mapped[dt.datetime] = mapped_column(DateTime())
    is_plugin_user: Mapped[bool] = mapped_column(Boolean, default=False)
    latest_snapshot_id: Mapped[int | None] = mapped_column(
        ForeignKey("player_snapshots.id", use_alter=True, name="fk_player_latest_snapshot"),
        nullable=True,
    )
    hiscores_updated_at: Mapped[dt.datetime | None] = mapped_column(DateTime(), nullable=True)


class PlayerSnapshot(Base):
    __tablename__ = "player_snapshots"

    id: Mapped[int] = mapped_column(primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id"), index=True)
    captured_at: Mapped[dt.datetime] = mapped_column(DateTime())
    source: Mapped[SnapshotSource] = mapped_column(Enum(SnapshotSource, native_enum=True))
    world: Mapped[int | None] = mapped_column(Integer, nullable=True)
    combat_level: Mapped[int | None] = mapped_column(Integer, nullable=True)
    skills: Mapped[dict | None] = mapped_column(JSON, nullable=True)
