from app.workers.hiscores_worker import compute_combat_level, parse_hiscores_json
from app.workers.items_worker import merge_item_data


def test_parse_hiscores_json_maps_skill_ids_to_names():
    data = {
        "skills": [
            {"id": 0, "level": 1247, "xp": 123456789},
            {"id": 1, "level": 99, "xp": 13034431},
            {"id": 24, "level": 50, "xp": 101333},
        ]
    }
    skills = parse_hiscores_json(data)
    assert skills["overall"] == {"level": 1247, "xp": 123456789}
    assert skills["attack"] == {"level": 99, "xp": 13034431}
    assert skills["sailing"] == {"level": 50, "xp": 101333}


def test_compute_combat_level_max_account_is_126():
    # A maxed-combat account (99 attack/strength/defence/hitpoints/ranged/prayer/magic)
    # has a real, well-known OSRS combat level of exactly 126.
    skills = {
        name: {"level": 99, "xp": 13034431}
        for name in ("attack", "strength", "defence", "hitpoints", "ranged", "prayer", "magic")
    }
    assert compute_combat_level(skills) == 126


def test_compute_combat_level_fresh_account_is_3():
    # A brand-new account (all combat skills at 1, 10 HP) has a real combat level of 3.
    skills = {
        "attack": {"level": 1, "xp": 0},
        "strength": {"level": 1, "xp": 0},
        "defence": {"level": 1, "xp": 0},
        "hitpoints": {"level": 10, "xp": 1154},
        "ranged": {"level": 1, "xp": 0},
        "prayer": {"level": 1, "xp": 0},
        "magic": {"level": 1, "xp": 0},
    }
    assert compute_combat_level(skills) == 3


def test_compute_combat_level_missing_skills_default_to_level_1():
    # An empty skills dict isn't the same as "fresh account" - a real fresh
    # account starts with 10 HP, not 1 (that case is covered separately
    # above). This just proves the defensive level-1 fallback doesn't crash
    # and produces the level a *literal* all-1s account would have.
    assert compute_combat_level({}) == 1


def test_merge_item_data_combines_mapping_and_price():
    mapping = [
        {"id": 4151, "name": "Abyssal whip", "highalch": 72000},
        {"id": 995, "name": "Coins", "highalch": 0},
    ]
    latest = {"data": {"4151": {"high": 2500000, "low": 2450000}}}

    merged = merge_item_data(mapping, latest)

    whip = next(item for item in merged if item["id"] == 4151)
    assert whip["name"] == "Abyssal whip"
    assert whip["ge_price"] == 2500000
    assert whip["ha_value"] == 72000

    coins = next(item for item in merged if item["id"] == 995)
    assert coins["ge_price"] is None


def test_merge_item_data_skips_entries_with_no_id():
    merged = merge_item_data([{"name": "Broken entry"}], {"data": {}})
    assert merged == []
