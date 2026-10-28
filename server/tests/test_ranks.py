from app.web.ranks import PICKABLE, rank_badge, rank_for, rank_icon


def test_known_ranks_match_runelite_values():
    assert [(r.value, r.name) for r in PICKABLE] == [
        (-1, "Not ranked"), (0, "Friend"), (1, "Recruit"), (2, "Corporal"), (3, "Sergeant"),
        (4, "Lieutenant"), (5, "Captain"), (6, "General"), (7, "Owner"),
    ]
    assert rank_for(127).name == "Jagex moderator"


def test_unknown_and_missing_ranks_still_render():
    assert rank_for(None).name == "Not ranked"
    assert rank_for(42).name == "Rank 42"
    assert "<img" not in rank_icon(-1)
    assert str(rank_badge(-1)).endswith("Not ranked")


def test_badge_has_icon_and_name():
    html = str(rank_badge(6))
    assert 'src="/static/img/ranks/general.png"' in html
    assert html.endswith(" General")
