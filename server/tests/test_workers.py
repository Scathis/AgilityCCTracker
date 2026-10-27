import datetime as dt

from app.workers.attendance_timeout_worker import TIMEOUT_S, should_close
from app.workers.items_worker import parse_item_mapping


def test_should_close_at_exactly_timeout_stays_open():
    now = dt.datetime.utcnow()
    last_sighted = now - dt.timedelta(seconds=TIMEOUT_S)
    assert should_close(last_sighted, now) is False


def test_should_close_past_timeout():
    now = dt.datetime.utcnow()
    last_sighted = now - dt.timedelta(seconds=TIMEOUT_S + 1)
    assert should_close(last_sighted, now) is True


def test_should_close_recently_sighted_stays_open():
    now = dt.datetime.utcnow()
    last_sighted = now - dt.timedelta(seconds=5)
    assert should_close(last_sighted, now) is False


def test_parse_item_mapping_extracts_id_and_name_only():
    mapping = [
        {"id": 4151, "name": "Abyssal whip", "highalch": 72000},
        {"id": 995, "name": "Coins", "highalch": 0},
    ]
    parsed = parse_item_mapping(mapping)

    assert parsed == [
        {"id": 4151, "name": "Abyssal whip"},
        {"id": 995, "name": "Coins"},
    ]
    # No price/value fields survive the parse - deliberately dropped.
    assert "highalch" not in parsed[0]
    assert "ge_price" not in parsed[0]


def test_parse_item_mapping_skips_entries_with_no_id():
    parsed = parse_item_mapping([{"name": "Broken entry"}])
    assert parsed == []
