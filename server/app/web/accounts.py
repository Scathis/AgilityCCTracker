"""Rules shared by self-registration, password changes and the admin users
page."""

import re

from app.models.user import User, UserRole

USERNAME_RE = re.compile(r"^[A-Za-z0-9 _-]{3,20}$")
PASSWORD_MIN = 10
PASSWORD_MAX = 128
# Stops a flood of sign-ups filling the approval queue.
MAX_PENDING = 25


def username_error(username: str) -> str | None:
    if not USERNAME_RE.match(username):
        return "Username must be 3-20 characters: letters, numbers, spaces, _ or -."
    return None


def password_error(password: str, confirm: str, username: str) -> str | None:
    if password != confirm:
        return "The passwords don't match."
    if not PASSWORD_MIN <= len(password) <= PASSWORD_MAX:
        return f"Password must be at least {PASSWORD_MIN} characters."
    if password.strip().lower() == username.strip().lower():
        return "Password can't be the same as the username."
    return None


def admin_change_error(actor: User, target: User) -> str | None:
    """Why an admin may not change/remove `target`, or None if they may."""
    if target.protected:
        return f"'{target.username}' is the owner account and can't be changed or removed."
    if target.id == actor.id:
        return "You can't change or remove your own account here."
    return None


def parse_role(value: str) -> UserRole | None:
    try:
        return UserRole(value)
    except ValueError:
        return None
