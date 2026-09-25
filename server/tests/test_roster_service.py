from app.services.roster import OpenSession, diff_roster


def test_new_member_opens_a_session():
    diff = diff_roster({"Alice"}, [], reporter_world=330)
    assert diff.to_open == ["Alice"]
    assert diff.to_close == []


def test_missing_member_closes_when_reporter_shares_the_session_world():
    open_sessions = [OpenSession(player_id=1, rsn="Bob", world=330)]
    diff = diff_roster(set(), open_sessions, reporter_world=330)
    assert diff.to_close == [1]


def test_missing_member_is_not_closed_when_reporter_is_on_a_different_world():
    open_sessions = [OpenSession(player_id=1, rsn="Bob", world=330)]
    diff = diff_roster(set(), open_sessions, reporter_world=331)
    assert diff.to_close == []


def test_nothing_closes_when_reporter_world_is_unknown():
    open_sessions = [OpenSession(player_id=1, rsn="Bob", world=330)]
    diff = diff_roster(set(), open_sessions, reporter_world=None)
    assert diff.to_close == []


def test_multiple_sessions_only_matching_world_closes():
    open_sessions = [
        OpenSession(player_id=1, rsn="Bob", world=330),
        OpenSession(player_id=2, rsn="Carl", world=331),
    ]
    diff = diff_roster(set(), open_sessions, reporter_world=330)
    assert diff.to_close == [1]


def test_present_member_neither_opens_nor_closes():
    open_sessions = [OpenSession(player_id=1, rsn="Bob", world=330)]
    diff = diff_roster({"Bob"}, open_sessions, reporter_world=330)
    assert diff.to_open == []
    assert diff.to_close == []
