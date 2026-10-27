import datetime as dt
import secrets

from helpers import _headers
from sqlalchemy import select

from app.models.item import Item
from app.models.kit import INVENTORY, Kit, KitItem
from app.models.user import UserRole


async def _items(db_session, *entries):
    """(id, name) or (id, name, slot) - slot is where it's worn, if it is."""
    now = dt.datetime.utcnow()
    for item_id, name, *rest in entries:
        slot = rest[0] if rest else None
        existing = (
            await db_session.execute(select(Item).where(Item.id == item_id))
        ).scalar_one_or_none()
        if existing is not None:
            existing.equip_slot = slot
            continue
        db_session.add(Item(id=item_id, name=name, updated_at=now, equip_slot=slot))
    await db_session.commit()


async def _latest_kit(db_session, name) -> Kit:
    result = await db_session.execute(select(Kit).where(Kit.name == name).order_by(Kit.id.desc()))
    return result.scalars().first()


async def _create_kit(client, db_session, csrf, name=None, **form) -> int:
    name = name or "Kit " + secrets.token_hex(3)
    response = await client.post(
        "/kits", data={"name": name, "csrf_token": csrf, **form}, follow_redirects=False
    )
    assert response.status_code == 303
    assert response.headers["location"] == "/kits"
    return (await _latest_kit(db_session, name)).id


async def _kit_items(db_session, kit_id):
    result = await db_session.execute(
        select(KitItem).where(KitItem.kit_id == kit_id).order_by(KitItem.slot, KitItem.item_id)
    )
    return result.scalars().all()


async def test_kits_page_has_info_box_and_create_button(client, make_user, login_as):
    login_as(await make_user(UserRole.STAFF))
    response = await client.get("/kits")
    assert response.status_code == 200
    assert "Recommended kits" in response.text
    assert 'href="/kits/new"' in response.text
    # The gear rules moved to Settings.
    assert "Flag runners with these slots empty" not in response.text


async def test_new_kit_page_asks_for_a_name_at_the_top(client, make_user, login_as):
    login_as(await make_user(UserRole.STAFF))
    response = await client.get("/kits/new")
    assert response.status_code == 200
    assert 'name="name"' in response.text
    assert "Save kit" in response.text
    assert 'name="slot_WEAPON"' in response.text


async def test_create_saves_everything_in_one_go(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    await _items(
        db_session,
        (9185, "Rune crossbow", "WEAPON"),
        (1540, "Anti-dragon shield", "SHIELD"),
        (12625, "Stamina potion(4)"),
    )
    kit_id = await _create_kit(
        client,
        db_session,
        csrf,
        "Full kit",
        slot_WEAPON="9185",
        slot_SHIELD="1540",
        slot_HEAD="",
        inv_item=["12625", "12625"],
        inv_qty=["2", "1"],
    )
    rows = await _kit_items(db_session, kit_id)
    assert sorted((r.slot, r.item_id, r.qty) for r in rows) == [
        ("INVENTORY", 12625, 3),  # duplicate lines merged
        ("SHIELD", 1540, 1),
        ("WEAPON", 9185, 1),
    ]


async def test_edit_replaces_the_kit_contents_and_name(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    await _items(
        db_session,
        (9185, "Rune crossbow", "WEAPON"),
        (861, "Magic shortbow", "WEAPON"),
        (12625, "Stamina potion(4)"),
    )
    kit_id = await _create_kit(
        client, db_session, csrf, "Before", slot_WEAPON="9185", inv_item=["12625"], inv_qty=["2"]
    )

    page = await client.get(f"/kits/{kit_id}/edit")
    assert page.status_code == 200
    assert "Rune crossbow" in page.text and "Stamina potion(4)" in page.text

    response = await client.post(
        f"/kits/{kit_id}",
        data={"name": "After", "slot_WEAPON": "861", "csrf_token": csrf},
        follow_redirects=False,
    )
    assert response.status_code == 303
    kit = (await db_session.execute(select(Kit).where(Kit.id == kit_id))).scalar_one()
    await db_session.refresh(kit)
    assert kit.name == "After"
    assert [(r.slot, r.item_id) for r in await _kit_items(db_session, kit_id)] == [("WEAPON", 861)]

    # Old detail URL still lands on the editor.
    redirect = await client.get(f"/kits/{kit_id}", follow_redirects=False)
    assert redirect.headers["location"] == f"/kits/{kit_id}/edit"


async def test_save_needs_a_name_and_known_items(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    no_name = await client.post("/kits", data={"name": "  ", "csrf_token": csrf})
    assert no_name.status_code == 422
    bad_item = await client.post(
        "/kits", data={"name": "Bad", "slot_HEAD": "999999999", "csrf_token": csrf}
    )
    assert bad_item.status_code == 404
    assert await _latest_kit(db_session, "Bad") is None


async def test_first_kit_becomes_default_and_default_can_move(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    for kit in (await db_session.execute(select(Kit))).scalars():
        for item in (
            await db_session.execute(select(KitItem).where(KitItem.kit_id == kit.id))
        ).scalars():
            await db_session.delete(item)
        await db_session.delete(kit)
    await db_session.commit()

    first = await _create_kit(client, db_session, csrf, "First")
    second = await _create_kit(client, db_session, csrf, "Second")
    kits = {k.id: k for k in (await db_session.execute(select(Kit))).scalars()}
    assert kits[first].is_default is True
    assert kits[second].is_default is False

    listing = await client.get("/kits")
    assert listing.text.count("Mark as default") == 1
    assert listing.text.count(">Edit<") == 2

    await client.post(f"/kits/{second}/default", data={"csrf_token": csrf})
    for k in kits.values():
        await db_session.refresh(k)
    assert kits[first].is_default is False
    assert kits[second].is_default is True

    # Deleting the default hands it to a remaining kit.
    await client.post(f"/kits/{second}/delete", data={"csrf_token": csrf})
    await db_session.refresh(kits[first])
    assert kits[first].is_default is True


async def test_search_fragment_targets_slot_or_inventory(
    client, make_user, login_as, default_group, db_session
):
    login_as(await make_user(UserRole.STAFF))
    await _items(db_session, (12625, "Stamina potion(4)"))
    none = await client.get("/kits/search", params={"q": "zzz nothing", "target": "WEAPON"})
    assert "No matches" in none.text
    inv = await client.get("/kits/search", params={"q": "Stamina potion", "target": "INVENTORY"})
    assert 'data-target="INVENTORY"' in inv.text and "Stamina potion(4)" in inv.text
    bad = await client.get("/kits/search", params={"q": "x", "target": "POCKET"})
    assert bad.status_code == 404


async def test_search_shows_untradeables_once_with_best_match_first(
    client, make_user, login_as, default_group, db_session
):
    login_as(await make_user(UserRole.STAFF))
    await _items(
        db_session,
        (10499, "Ava's accumulator", "CAPE"),
        (23609, "Ava's accumulator", "CAPE"),  # cache duplicate
        (13337, "Accumulator max cape", "CAPE"),
        (13338, "Accumulator max hood", "HEAD"),
    )
    page = await client.get("/kits/search", params={"q": "accumulator", "target": "CAPE"})
    assert page.text.count('data-name="Ava') == 1
    assert 'data-id="10499"' in page.text
    assert 'data-id="23609"' not in page.text
    # Only items worn in the slot being filled.
    assert "Accumulator max hood" not in page.text
    head = await client.get("/kits/search", params={"q": "accumulator", "target": "HEAD"})
    assert "Accumulator max hood" in head.text and "Ava" not in head.text


async def test_save_refuses_an_item_in_the_wrong_slot(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    await _items(db_session, (1079, "Rune platelegs", "LEGS"))
    response = await client.post(
        "/kits", data={"name": "Wrong slot", "slot_TORSO": "1079", "csrf_token": csrf}
    )
    assert response.status_code == 422
    assert "isn't worn in the Body slot" in response.text


async def test_status_carries_gear_rules_and_kits(client, fc_group, db_session):
    group, token = fc_group
    kit = Kit(fc_group_id=group.id, name="Std", is_default=True)
    db_session.add(kit)
    await db_session.flush()
    db_session.add_all(
        [
            KitItem(kit_id=kit.id, slot="WEAPON", item_id=861, qty=1),
            KitItem(kit_id=kit.id, slot=INVENTORY, item_id=12625, qty=2),
        ]
    )
    await db_session.commit()

    body = (await client.get("/api/v1/status", headers=_headers(token))).json()
    assert body["gear_rules"] == {
        "required_slots": ["HEAD", "AMULET", "TORSO", "LEGS", "BOOTS"],
        "ranged_weapon": True,
    }
    assert body["kits"] == [
        {
            "id": kit.id,
            "name": "Std",
            "is_default": True,
            "equipment": [{"slot": "WEAPON", "item_id": 861}],
            # The course entry fee is always first and always there.
            "inventory": [{"item_id": 995, "qty": 150000}, {"item_id": 12625, "qty": 2}],
        }
    ]
    assert "loadouts" not in body
    assert "crossbow_counts_as_bow_at_dd" not in body["dd"]


async def test_entry_fee_is_locked_into_every_kit(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    await _items(db_session, (12625, "Stamina potion(4)"))
    # Posting coins yourself is ignored - the fee is fixed and added when served.
    kit_id = await _create_kit(
        client,
        db_session,
        csrf,
        "Fee kit",
        inv_item=["995", "12625"],
        inv_qty=["5", "1"],
    )
    rows = await _kit_items(db_session, kit_id)
    assert [(r.item_id, r.qty) for r in rows] == [(12625, 1)]

    page = await client.get(f"/kits/{kit_id}/edit")
    assert "Course entry fee" in page.text and "150,000" in page.text

    # 27 other items is the limit (the fee takes the 28th slot).
    many = await client.post(
        "/kits",
        data={
            "name": "Too many",
            "inv_item": [str(i) for i in range(1, 29)],
            "inv_qty": ["1"] * 28,
            "csrf_token": csrf,
        },
    )
    assert many.status_code == 422
