import datetime as dt


def from_epoch_ms(ms: int) -> dt.datetime:
    """Naive UTC datetime, matching this project's DATETIME(3)-in-UTC convention (§6.2)."""
    return dt.datetime.fromtimestamp(ms / 1000, tz=dt.UTC).replace(tzinfo=None)


def to_epoch_ms(value: dt.datetime) -> int:
    return int(value.replace(tzinfo=dt.UTC).timestamp() * 1000)
