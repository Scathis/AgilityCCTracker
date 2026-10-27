import datetime as dt

from sqlalchemy import Boolean, DateTime, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Player(Base):
    __tablename__ = "players"

    id: Mapped[int] = mapped_column(primary_key=True)
    rsn: Mapped[str] = mapped_column(String(12), unique=True)
    first_seen: Mapped[dt.datetime] = mapped_column(DateTime())
    last_seen: Mapped[dt.datetime] = mapped_column(DateTime())
    is_plugin_user: Mapped[bool] = mapped_column(Boolean, default=False)
