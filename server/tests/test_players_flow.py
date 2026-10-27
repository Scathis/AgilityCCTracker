import datetime as dt

from app.models.player import Player
from app.models.user import UserRole


async def test_players_list_and_profile_render(client, make_user, login_as, db_session):
    user = await make_user(UserRole.STAFF)
    login_as(user)

    player = Player(
        rsn="ProfileTest",
        first_seen=dt.datetime.utcnow(),
        last_seen=dt.datetime.utcnow(),
        is_plugin_user=True,
    )
    db_session.add(player)
    await db_session.commit()
    await db_session.refresh(player)

    list_response = await client.get("/players", params={"q": "ProfileTest"})
    assert list_response.status_code == 200
    assert "ProfileTest" in list_response.text

    profile_response = await client.get(f"/players/{player.id}")
    assert profile_response.status_code == 200
    assert "ProfileTest" in profile_response.text
