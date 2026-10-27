import datetime as dt

from app.workers.attendance_timeout_worker import TIMEOUT_S, should_close
from app.workers.items_worker import parse_equip_slots, parse_item_names


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


def test_parse_item_names_keeps_untradeables_and_skips_placeholders():
    parsed = parse_item_names(
        {"4151": "Abyssal whip", "10499": "Ava's accumulator", "617": "null", "9": " ", "x": "Bad"}
    )
    assert parsed == [
        {"id": 4151, "name": "Abyssal whip"},
        {"id": 10499, "name": "Ava's accumulator"},
    ]


def test_parse_equip_slots_maps_runelite_slot_indexes():
    stats = {
        "10499": {"equipable": True, "equipment": {"slot": 1}},
        "1079": {"equipable": True, "equipment": {"slot": 7}},
        "12625": {"weight": 0.02},
        "99": {"equipable": True, "equipment": {"slot": 6}},
    }
    assert parse_equip_slots(stats) == {10499: "CAPE", 1079: "LEGS"}
