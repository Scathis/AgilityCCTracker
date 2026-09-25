"""Roster diffing - BUILD_PLAN.md §6.4 step 5.

The diff itself (`diff_roster`) is pure Python, no DB access, so it's
directly unit-testable per §6.7's explicit call-out ("roster diff (join/leave,
multiple reporters, world hop)").
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class OpenSession:
    """An existing open (left_at is null) attendance row, as far as diffing cares."""

    player_id: int
    rsn: str
    world: int


@dataclass(frozen=True)
class RosterDiff:
    to_open: list[str]
    to_close: list[int]


def diff_roster(
    snapshot_rsns: set[str],
    open_sessions: list[OpenSession],
    reporter_world: int | None,
) -> RosterDiff:
    """A name present in the snapshot but with no open session gets opened.

    A name absent from the snapshot only gets its session closed if the
    reporter's own world is confidently known AND matches that session's
    world - a reporter who world-hopped can no longer see the old world's
    members, so their absence from *this* snapshot doesn't mean they left.
    If `reporter_world` is None (unknown), nothing is ever closed this round -
    conservative by design, matching "avoid closing sessions based on a
    reporter who hopped."
    """
    open_by_rsn = {s.rsn: s for s in open_sessions}

    to_open = [rsn for rsn in snapshot_rsns if rsn not in open_by_rsn]

    to_close: list[int] = []
    if reporter_world is not None:
        for session in open_sessions:
            if session.rsn not in snapshot_rsns and session.world == reporter_world:
                to_close.append(session.player_id)

    return RosterDiff(to_open=to_open, to_close=to_close)
