import datetime as dt

from sqlalchemy import DateTime, ForeignKey, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Death(Base):
    __tablename__ = "deaths"
    __table_args__ = (UniqueConstraint("player_id", "died_at", name="uq_deaths_player_diedat"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    player_id: Mapped[int] = mapped_column(ForeignKey("players.id"), index=True)
    event_id: Mapped[int | None] = mapped_column(ForeignKey("events.id"), nullable=True)
    died_at: Mapped[dt.datetime] = mapped_column(DateTime())
