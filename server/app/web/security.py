"""Dashboard-side auth primitives - distinct from app/security.py (plugin
token auth). Argon2 password hashing + a signed, timed session cookie
carrying the CSRF token, per BUILD_PLAN.md §6.3.
"""

import secrets

from argon2 import PasswordHasher
from argon2.exceptions import VerifyMismatchError
from itsdangerous import BadSignature, SignatureExpired, URLSafeTimedSerializer

from app.config import settings

SESSION_COOKIE_NAME = "session"
SESSION_MAX_AGE_SECONDS = 7 * 24 * 3600
FLASH_COOKIE_NAME = "flash"
FLASH_MAX_AGE_SECONDS = 60

_hasher = PasswordHasher()
_serializer = URLSafeTimedSerializer(settings.app_secret, salt="dashboard-session")
_flash_serializer = URLSafeTimedSerializer(settings.app_secret, salt="dashboard-flash")


def hash_password(password: str) -> str:
    return _hasher.hash(password)


def verify_password(password: str, password_hash: str) -> bool:
    try:
        return _hasher.verify(password_hash, password)
    except VerifyMismatchError:
        return False


def new_session_payload(user_id: int) -> dict:
    return {"user_id": user_id, "csrf": secrets.token_urlsafe(24)}


def sign_session(payload: dict) -> str:
    return _serializer.dumps(payload)


def read_session(cookie_value: str) -> dict | None:
    try:
        return _serializer.loads(cookie_value, max_age=SESSION_MAX_AGE_SECONDS)
    except (BadSignature, SignatureExpired):
        return None


def sign_flash(message: str) -> str:
    """One-time, short-lived reveal for a generated password/token - never put
    a secret in a query string (browser history, server access logs)."""
    return _flash_serializer.dumps({"message": message})


def read_flash(cookie_value: str) -> str | None:
    try:
        return _flash_serializer.loads(cookie_value, max_age=FLASH_MAX_AGE_SECONDS).get("message")
    except (BadSignature, SignatureExpired):
        return None
