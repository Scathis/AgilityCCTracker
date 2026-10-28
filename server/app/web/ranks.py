"""Friends chat ranks as the game shows them: the value RuneLite's
FriendsChatRank reports (what the plugin sends and Settings stores), the
name, and its chat icon. Icons are the OSRS Wiki's copies of the in-game
sprites (oldschool.runescape.wiki/w/Chat-channel), served from
static/img/ranks/."""

from dataclasses import dataclass

from markupsafe import Markup, escape


@dataclass(frozen=True)
class Rank:
    value: int
    name: str
    icon: str | None


UNRANKED = Rank(-1, "Not ranked", None)

RANKS = [
    UNRANKED,
    Rank(0, "Friend", "friend"),
    Rank(1, "Recruit", "recruit"),
    Rank(2, "Corporal", "corporal"),
    Rank(3, "Sergeant", "sergeant"),
    Rank(4, "Lieutenant", "lieutenant"),
    Rank(5, "Captain", "captain"),
    Rank(6, "General", "general"),
    Rank(7, "Owner", "owner"),
    Rank(127, "Jagex moderator", "jmod"),
]

_BY_VALUE = {r.value: r for r in RANKS}

# What Settings offers as a minimum rank (a JMod minimum would mean nobody).
PICKABLE = [r for r in RANKS if r.value != 127]


def rank_for(value: int | None) -> Rank:
    if value is None:
        return UNRANKED
    return _BY_VALUE.get(value, Rank(value, f"Rank {value}", None))


def rank_icon(value: int | None) -> Markup:
    """Just the icon (an empty fixed-width slot when unranked), titled with
    the rank name for hover."""
    rank = rank_for(value)
    if rank.icon is None:
        return Markup('<span class="rank-icon" title="{}"></span>').format(rank.name)
    return Markup(
        '<span class="rank-icon" title="{0}"><img src="/static/img/ranks/{1}.png" alt="{0}"></span>'
    ).format(rank.name, rank.icon)


def rank_badge(value: int | None) -> Markup:
    """Icon plus name, for tables and pickers."""
    return rank_icon(value) + Markup(" ") + escape(rank_for(value).name)
