import datetime as dt

from sqlalchemy import DateTime, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Item(Base):
    """Just the name/id catalog the loadout picker searches against -
    deliberately never prices (see docs/BUILD_LOG.md: dropped by owner
    request, both as an unused-dead-code cleanup and because a gear/risk
    value calculator was judged a real Plugin Hub approval risk)."""

    __tablename__ = "items"

    id: Mapped[int] = mapped_column(primary_key=True)  # real OSRS item id, not autoincrement
    name: Mapped[str] = mapped_column(String(255))
    updated_at: Mapped[dt.datetime] = mapped_column(DateTime())
