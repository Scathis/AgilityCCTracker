import datetime as dt

from sqlalchemy import JSON, Boolean, DateTime, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class FcGroup(Base):
    __tablename__ = "fc_groups"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(255))
    owner_rsn: Mapped[str] = mapped_column(String(12))
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime())

    observer_enabled: Mapped[bool] = mapped_column(Boolean, default=True)
    # Lowest FC rank (RuneLite FriendsChatRank value: -1 unranked, 0 friend, 1 recruit, ...)
    # that the plugin shows other players' gear gaps to (sidebar list + over-head flags).
    flag_viewer_min_rank: Mapped[int] = mapped_column(Integer, default=0, server_default="0")
    # Runner gear rules (dashboard Kits page): slots that must not be empty
    # (NULL = services.gear_rules.DEFAULT_REQUIRED_SLOTS), and whether the
    # weapon must be a bow/crossbow (a crossbow also needing a shield).
    required_slots: Mapped[list | None] = mapped_column(JSON, nullable=True)
    require_ranged_weapon: Mapped[bool] = mapped_column(Boolean, default=True, server_default="1")
    dd_tiles: Mapped[list | None] = mapped_column(JSON, nullable=True)
    dd_min_rank: Mapped[int] = mapped_column(Integer, default=3)
    dd_arrive_grace_s: Mapped[int] = mapped_column(Integer, default=15)
    dd_leave_grace_s: Mapped[int] = mapped_column(Integer, default=5)
    dd_max_minutes: Mapped[int] = mapped_column(Integer, default=10)
