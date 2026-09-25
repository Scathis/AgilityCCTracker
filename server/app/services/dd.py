"""DD call/presence merging - BUILD_PLAN.md §6.4 step 7.

All pure, DB-free functions (per §6.7's "DD call merge within ±10s", "DD
presence merge (max/min/OR/union)") so they're directly unit-testable.
"""

from dataclasses import dataclass, field


def find_matching_call(
    existing_calls: list[tuple[int, int]], called_at: int, tolerance_ms: int = 10_000
) -> int | None:
    """`existing_calls` is (call_id, called_at_epoch_ms) for the event's DD calls.
    Returns the matching call_id within ±tolerance_ms, or None if a new call is needed."""
    for call_id, existing_called_at in existing_calls:
        if abs(existing_called_at - called_at) <= tolerance_ms:
            return call_id
    return None


def merge_broken_at(current: int | None, incoming: int | None, staff_edited: bool) -> int | None:
    """broken_at = earliest non-null reported time, unless staff has manually edited it."""
    if staff_edited:
        return current
    candidates = [v for v in (current, incoming) if v is not None]
    return min(candidates) if candidates else None


@dataclass(frozen=True)
class PresenceMerge:
    arrived_after_ms: int | None
    ms_on_tile: int
    left_before_break: bool
    seen_running: bool
    observed_ms: int
    dd_loadout_failed: tuple[str, ...] = field(default_factory=tuple)
    sources: tuple[str, ...] = field(default_factory=tuple)


def merge_presence(
    existing: PresenceMerge | None, incoming: PresenceMerge, reporter: str
) -> PresenceMerge:
    """Idempotent, order-independent merge: max/min/OR/union per field, per §6.4 step 7."""
    if existing is None:
        return PresenceMerge(
            arrived_after_ms=incoming.arrived_after_ms,
            ms_on_tile=incoming.ms_on_tile,
            left_before_break=incoming.left_before_break,
            seen_running=incoming.seen_running,
            observed_ms=incoming.observed_ms,
            dd_loadout_failed=tuple(sorted(set(incoming.dd_loadout_failed))),
            sources=(reporter,),
        )

    arrived_candidates = [
        v for v in (existing.arrived_after_ms, incoming.arrived_after_ms) if v is not None
    ]
    merged_arrived = min(arrived_candidates) if arrived_candidates else None

    return PresenceMerge(
        arrived_after_ms=merged_arrived,
        ms_on_tile=max(existing.ms_on_tile, incoming.ms_on_tile),
        left_before_break=existing.left_before_break or incoming.left_before_break,
        seen_running=existing.seen_running or incoming.seen_running,
        observed_ms=max(existing.observed_ms, incoming.observed_ms),
        dd_loadout_failed=tuple(
            sorted(set(existing.dd_loadout_failed) | set(incoming.dd_loadout_failed))
        ),
        sources=tuple(sorted(set(existing.sources) | {reporter})),
    )
