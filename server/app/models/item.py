import datetime as dt

from sqlalchemy import DateTime, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Item(Base):
    """Just the name/id/slot catalog the Kits item search runs against -
    deliberately never prices (see docs/BUILD_LOG.md: dropped by owner
    request, both as an unused-dead-code cleanup and because a gear/risk
    value calculator was judged a real Plugin Hub approval risk)."""

    __tablename__ = "items"

    id: Mapped[int] = mapped_column(primary_key=True)  # real OSRS item id, not autoincrement
    name: Mapped[str] = mapped_column(String(255))
    # Where it's worn (HEAD, CAPE, ... as in services.gear_rules.SLOT_ORDER),
    # or NULL if it can't be worn. Kit slot pickers only offer matching items.
    equip_slot: Mapped[str | None] = mapped_column(String(12), nullable=True, index=True)
    updated_at: Mapped[dt.datetime] = mapped_column(DateTime())
