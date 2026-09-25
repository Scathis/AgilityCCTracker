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
    dd_tiles: Mapped[list | None] = mapped_column(JSON, nullable=True)
    dd_call_regex: Mapped[str | None] = mapped_column(String(255), nullable=True)
    dd_break_regex: Mapped[str | None] = mapped_column(String(255), nullable=True)
    dd_min_rank: Mapped[int] = mapped_column(Integer, default=3)
    dd_arrive_grace_s: Mapped[int] = mapped_column(Integer, default=15)
    dd_leave_grace_s: Mapped[int] = mapped_column(Integer, default=5)
    dd_max_minutes: Mapped[int] = mapped_column(Integer, default=10)
    dd_crossbow_counts_as_bow: Mapped[bool] = mapped_column(Boolean, default=True)
