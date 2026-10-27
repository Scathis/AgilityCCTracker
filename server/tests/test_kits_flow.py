import datetime as dt
import secrets

from helpers import _headers
from sqlalchemy import select

from app.models.item import Item
from app.models.kit import INVENTORY, Kit, KitItem
from app.models.user import UserRole


async def _items(db_session, *pairs):
    now = dt.datetime.utcnow()
    for item_id, name in pairs:
        if (await db_session.execute(select(Item).where(Item.id == item_id))).scalar_one_or_none():
            continue
        db_session.add(Item(id=item_id, name=name, updated_at=now))
    await db_session.commit()


async def _create_kit(client, csrf, name=None) -> int:
    name = name or "Kit " + secrets.token_hex(3)
    response = await client.post(
        "/kits", data={"name": name, "csrf_token": csrf}, follow_redirects=False
    )
    assert response.status_code == 303
    return int(response.headers["location"].rsplit("/", 1)[1])


async def _kit_items(db_session, kit_id):
    result = await db_session.execute(
        select(KitItem).where(KitItem.kit_id == kit_id).order_by(KitItem.id)
    )
    return result.scalars().all()


async def test_kits_page_renders_rules_with_defaults(client, make_user, login_as, default_group):
    login_as(await make_user(UserRole.STAFF))
    response = await client.get("/kits")
    assert response.status_code == 200
    assert "Flag runners with these slots empty" in response.text
    assert "Weapon must be a bow or crossbow" in response.text


async def test_saving_gear_rules_flows_to_status(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    response = await client.post(
        "/kits/rules",
        data={"required": ["BOOTS", "HEAD", "WEAPON"], "csrf_token": csrf},
        follow_redirects=False,
    )
    assert response.status_code == 303
    await db_session.refresh(default_group)
    # WEAPON is handled by the ranged-weapon rule, never as a plain "not empty" slot.
    assert default_group.required_slots == ["HEAD", "BOOTS"]
    assert default_group.require_ranged_weapon is False

    await client.post(
        "/kits/rules",
        data={
            "required": ["HEAD", "AMULET", "TORSO", "LEGS", "BOOTS"],
            "ranged_weapon": "on",
            "csrf_token": csrf,
        },
    )
    await db_session.refresh(default_group)
    assert default_group.required_slots == ["HEAD", "AMULET", "TORSO", "LEGS", "BOOTS"]
    assert default_group.require_ranged_weapon is True


async def test_first_kit_becomes_default_and_default_can_move(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    for kit in (await db_session.execute(select(Kit))).scalars():
        await db_session.delete(kit)
    await db_session.commit()

    first = await _create_kit(client, csrf, "First")
    second = await _create_kit(client, csrf, "Second")
    kits = {k.id: k for k in (await db_session.execute(select(Kit))).scalars()}
    assert kits[first].is_default is True
    assert kits[second].is_default is False

    await client.post(f"/kits/{second}/default", data={"csrf_token": csrf})
    for k in kits.values():
        await db_session.refresh(k)
    assert kits[first].is_default is False
    assert kits[second].is_default is True

    # Deleting the default hands it to a remaining kit.
    await client.post(f"/kits/{second}/delete", data={"csrf_token": csrf})
    await db_session.refresh(kits[first])
    assert kits[first].is_default is True


async def test_equipment_slot_holds_one_item_and_can_be_cleared(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    await _items(db_session, (9185, "Rune crossbow"), (861, "Magic shortbow"))
    kit_id = await _create_kit(client, csrf)

    for item_id in (9185, 861):
        response = await client.post(
            f"/kits/{kit_id}/slots/WEAPON/item",
            data={"item_id": str(item_id), "csrf_token": csrf},
            follow_redirects=False,
        )
        assert response.status_code == 303
    rows = await _kit_items(db_session, kit_id)
    assert [(r.slot, r.item_id) for r in rows] == [("WEAPON", 861)]

    detail = await client.get(f"/kits/{kit_id}")
    assert "Magic shortbow" in detail.text
    assert 'name="q"' in detail.text

    await client.post(f"/kits/{kit_id}/slots/WEAPON/clear", data={"csrf_token": csrf})
    assert await _kit_items(db_session, kit_id) == []


async def test_inventory_lines_merge_and_quantities_update(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    await _items(db_session, (12625, "Stamina potion(4)"))
    kit_id = await _create_kit(client, csrf)

    for qty in ("2", "1"):
        await client.post(
            f"/kits/{kit_id}/inventory",
            data={"item_id": "12625", "qty": qty, "csrf_token": csrf},
        )
    rows = await _kit_items(db_session, kit_id)
    assert [(r.slot, r.item_id, r.qty) for r in rows] == [(INVENTORY, 12625, 3)]

    await client.post(
        f"/kits/{kit_id}/inventory/{rows[0].id}/qty", data={"qty": "5", "csrf_token": csrf}
    )
    await db_session.refresh(rows[0])
    assert rows[0].qty == 5

    fragment = await client.post(
        f"/kits/{kit_id}/inventory/{rows[0].id}/delete",
        data={"csrf_token": csrf},
        headers={"HX-Request": "true"},
    )
    assert fragment.status_code == 200
    assert "Nothing to carry yet" in fragment.text
    assert await _kit_items(db_session, kit_id) == []


async def test_unknown_item_and_unknown_slot_rejected(
    client, make_user, login_as, default_group, db_session
):
    csrf = login_as(await make_user(UserRole.STAFF))
    kit_id = await _create_kit(client, csrf)
    bad_item = await client.post(
        f"/kits/{kit_id}/slots/HEAD/item", data={"item_id": "999999999", "csrf_token": csrf}
    )
    assert bad_item.status_code == 404
    bad_slot = await client.post(
        f"/kits/{kit_id}/slots/POCKET/item", data={"item_id": "1", "csrf_token": csrf}
    )
    assert bad_slot.status_code == 404


async def test_search_fragments(client, make_user, login_as, default_group, db_session):
    login_as(await make_user(UserRole.STAFF))
    await _items(db_session, (12625, "Stamina potion(4)"))
    csrf = login_as(await make_user(UserRole.STAFF))
    kit_id = await _create_kit(client, csrf)
    slot = await client.get(f"/kits/{kit_id}/slots/WEAPON/search", params={"q": "zzz nothing"})
    assert "No matches" in slot.text
    inv = await client.get(f"/kits/{kit_id}/inventory/search", params={"q": "Stamina potion"})
    assert "Stamina potion(4)" in inv.text


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
            "inventory": [{"item_id": 12625, "qty": 2}],
        }
    ]
    assert "loadouts" not in body
    assert "crossbow_counts_as_bow_at_dd" not in body["dd"]
