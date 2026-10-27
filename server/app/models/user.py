import datetime as dt
import enum

from sqlalchemy import Boolean, DateTime, Enum, String
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base


class UserRole(enum.StrEnum):
    ADMIN = "admin"
    STAFF = "staff"
    VIEWER = "viewer"


class UserStatus(enum.StrEnum):
    # Self-registered accounts wait for an admin; only ACTIVE accounts can log in.
    PENDING = "pending"
    ACTIVE = "active"


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(255), unique=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    role: Mapped[UserRole] = mapped_column(Enum(UserRole, native_enum=True))
    created_at: Mapped[dt.datetime] = mapped_column(DateTime())
    last_login_at: Mapped[dt.datetime | None] = mapped_column(DateTime(), nullable=True)
    status: Mapped[UserStatus] = mapped_column(
        Enum(UserStatus, native_enum=True), default=UserStatus.ACTIVE, server_default="ACTIVE"
    )
    # The owner account: can't be removed, have its role changed or its
    # password reset from the dashboard (protects against accidents and
    # against another admin taking it over).
    protected: Mapped[bool] = mapped_column(Boolean, default=False, server_default="0")
    # Set when an admin resets the password: the temporary one only gets
    # them as far as the Account page, where they must choose their own.
    must_change_password: Mapped[bool] = mapped_column(
        Boolean, default=False, server_default="0"
    )
