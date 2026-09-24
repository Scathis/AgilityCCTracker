import datetime as dt
import enum

from sqlalchemy import DateTime, Enum, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class UserRole(enum.StrEnum):
    ADMIN = "admin"
    STAFF = "staff"
    VIEWER = "viewer"


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(255), unique=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    role: Mapped[UserRole] = mapped_column(Enum(UserRole, native_enum=True))
    created_at: Mapped[dt.datetime] = mapped_column(DateTime())
    last_login_at: Mapped[dt.datetime | None] = mapped_column(DateTime(), nullable=True)
