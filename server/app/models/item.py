import datetime as dt

from sqlalchemy import DateTime, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class Item(Base):
    __tablename__ = "items"

    id: Mapped[int] = mapped_column(primary_key=True)  # real OSRS item id, not autoincrement
    name: Mapped[str] = mapped_column(String(255))
    ge_price: Mapped[int | None] = mapped_column(Integer, nullable=True)
    ha_value: Mapped[int | None] = mapped_column(Integer, nullable=True)
    updated_at: Mapped[dt.datetime] = mapped_column(DateTime())
