from app.services.dd import PresenceMerge, find_matching_call, merge_broken_at, merge_presence


def test_find_matching_call_within_tolerance():
    assert find_matching_call([(1, 1_000_000)], called_at=1_005_000) == 1


def test_find_matching_call_outside_tolerance():
    assert find_matching_call([(1, 1_000_000)], called_at=1_020_000) is None


def test_find_matching_call_empty_list():
    assert find_matching_call([], called_at=1_000_000) is None


def test_merge_broken_at_both_none():
    assert merge_broken_at(None, None, staff_edited=False) is None


def test_merge_broken_at_one_side_set():
    assert merge_broken_at(None, 5_000, staff_edited=False) == 5_000
    assert merge_broken_at(5_000, None, staff_edited=False) == 5_000


def test_merge_broken_at_earliest_wins():
    assert merge_broken_at(9_000, 5_000, staff_edited=False) == 5_000
    assert merge_broken_at(5_000, 9_000, staff_edited=False) == 5_000


def test_merge_broken_at_staff_edited_freezes_current():
    assert merge_broken_at(5_000, 1_000, staff_edited=True) == 5_000
    assert merge_broken_at(5_000, None, staff_edited=True) == 5_000


def _presence(**overrides):
    defaults = dict(
        arrived_after_ms=1_000,
        ms_on_tile=2_000,
        left_before_break=False,
        seen_running=False,
        observed_ms=3_000,
        dd_loadout_failed=(),
    )
    defaults.update(overrides)
    return PresenceMerge(**defaults)


def test_merge_presence_first_observation_wraps_incoming():
    merged = merge_presence(None, _presence(), reporter="Alice")
    assert merged.sources == ("Alice",)
    assert merged.ms_on_tile == 2_000


def test_merge_presence_takes_max_of_durations():
    existing = _presence(ms_on_tile=1_000, observed_ms=1_000, sources=("Alice",))
    incoming = _presence(ms_on_tile=5_000, observed_ms=2_000)
    merged = merge_presence(existing, incoming, reporter="Bob")
    assert merged.ms_on_tile == 5_000
    assert merged.observed_ms == 2_000


def test_merge_presence_takes_min_of_arrived_after_ms():
    existing = _presence(arrived_after_ms=5_000, sources=("Alice",))
    incoming = _presence(arrived_after_ms=1_000)
    merged = merge_presence(existing, incoming, reporter="Bob")
    assert merged.arrived_after_ms == 1_000


def test_merge_presence_handles_one_sided_arrived_after_ms():
    existing = _presence(arrived_after_ms=None, sources=("Alice",))
    incoming = _presence(arrived_after_ms=1_000)
    merged = merge_presence(existing, incoming, reporter="Bob")
    assert merged.arrived_after_ms == 1_000


def test_merge_presence_ors_the_flags():
    existing = _presence(left_before_break=True, seen_running=False, sources=("Alice",))
    incoming = _presence(left_before_break=False, seen_running=True)
    merged = merge_presence(existing, incoming, reporter="Bob")
    assert merged.left_before_break is True
    assert merged.seen_running is True


def test_merge_presence_unions_failed_checks_and_sources():
    existing = _presence(dd_loadout_failed=("dd-bow",), sources=("Alice",))
    incoming = _presence(dd_loadout_failed=("dd-no-xbow",))
    merged = merge_presence(existing, incoming, reporter="Bob")
    assert set(merged.dd_loadout_failed) == {"dd-bow", "dd-no-xbow"}
    assert set(merged.sources) == {"Alice", "Bob"}
