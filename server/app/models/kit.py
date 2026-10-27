from sqlalchemy import Boolean, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base

INVENTORY = "INVENTORY"

# Every runner kit carries the Wilderness Agility Course entry fee: 150,000
# coins in one inventory slot. Added to every kit when it's served (never
# stored), so it can't be forgotten or removed on the dashboard.
COINS_ITEM_ID = 995
COURSE_FEE_COINS = 150_000


class Kit(Base):
    """A recommended runner setup (what to wear, what to carry), built on the
    dashboard's Kits page and sent to the plugin for its local pre-course check
    and bank highlighting. Advisory only - flags come from the gear rules."""

    __tablename__ = "kits"

    id: Mapped[int] = mapped_column(primary_key=True)
    fc_group_id: Mapped[int] = mapped_column(ForeignKey("fc_groups.id"), index=True)
    name: Mapped[str] = mapped_column(String(64))
    is_default: Mapped[bool] = mapped_column(Boolean, default=False)


class KitItem(Base):
    """One item in a kit: an equipment slot (one item per slot) or, with
    slot == INVENTORY, something to carry with a quantity."""

    __tablename__ = "kit_items"

    id: Mapped[int] = mapped_column(primary_key=True)
    kit_id: Mapped[int] = mapped_column(ForeignKey("kits.id", ondelete="CASCADE"), index=True)
    slot: Mapped[str] = mapped_column(String(16))
    item_id: Mapped[int] = mapped_column(Integer)
    qty: Mapped[int] = mapped_column(Integer, default=1)
