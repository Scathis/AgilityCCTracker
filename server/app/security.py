import hashlib
import hmac
import secrets
import time
from collections import defaultdict, deque

from app.config import settings


def generate_token() -> str:
    return secrets.token_urlsafe(32)


def hash_token(token: str) -> str:
    """HMAC-SHA256(APP_SECRET, token) hex - never store the raw token."""
    return hmac.new(
        settings.app_secret.encode("utf-8"), token.encode("utf-8"), hashlib.sha256
    ).hexdigest()


def verify_token(token: str, token_hash: str) -> bool:
    return hmac.compare_digest(hash_token(token), token_hash)


class RateLimiter:
    """In-memory sliding-window failed-auth limiter: N failures per key per window.

    In-memory is fine per BUILD_PLAN.md §6.3 ("single process is fine") -
    this server runs as one Uvicorn worker, no cross-process state needed.
    """

    def __init__(self, max_failures: int = 5, window_seconds: float = 300.0) -> None:
        self.max_failures = max_failures
        self.window_seconds = window_seconds
        self._failures: dict[str, deque[float]] = defaultdict(deque)

    def record_failure(self, key: str) -> None:
        now = time.monotonic()
        self._prune(key, now)
        self._failures[key].append(now)

    def is_limited(self, key: str) -> bool:
        now = time.monotonic()
        self._prune(key, now)
        return len(self._failures[key]) >= self.max_failures

    def reset(self, key: str) -> None:
        self._failures.pop(key, None)

    def clear_all(self) -> None:
        """Test-only: wipe every tracked key so one test's failures can't bleed into another."""
        self._failures.clear()

    def _prune(self, key: str, now: float) -> None:
        bucket = self._failures[key]
        while bucket and now - bucket[0] > self.window_seconds:
            bucket.popleft()


auth_rate_limiter = RateLimiter(max_failures=5, window_seconds=300.0)

# Separate instance from auth_rate_limiter (plugin token auth) so failures
# on one surface never lock out the other from the same IP - different
# attack surfaces, different callers, should tune/trip independently even
# though they currently share the same 5-per-5-minutes shape.
dashboard_login_rate_limiter = RateLimiter(max_failures=5, window_seconds=300.0)

# Self-registration on the dashboard: at most 3 sign-ups per IP per hour
# (in memory only, like the other limiters - IPs are never stored).
registration_rate_limiter = RateLimiter(max_failures=3, window_seconds=3600.0)
