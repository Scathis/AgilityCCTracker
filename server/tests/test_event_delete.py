import datetime as dt
import secrets

from sqlalchemy import func, select

from app.models.dd import DdCall, DdPresence
from app.models.death import Death
from app.models.event import (
    Attendance,
    Event,
    EventParticipant,
    ParticipantRole,
    ParticipantStatus,
    ParticipantStint,
)
from app.models.lap import Lap
from app.models.player import Player
from app.models.user import UserRole


async def _mass_with_data(db_session, group, *, ended=True) -> tuple[Event, Player]:
    now = dt.datetime.utcnow()
    event = Event(
        fc_group_id=group.id,
        name=f"Test mass {secrets.token_hex(3)}",
        started_at=now - dt.timedelta(hours=1),
        ended_at=now if ended else None,
    )
    player = Player(rsn=f"del{secrets.token_hex(4)}", first_seen=now, last_seen=now)
    db_session.add_all([event, player])
    await db_session.flush()
    call = DdCall(event_id=event.id, called_at=now, reports=1)
    db_session.add_all(
        [
            call,
            Attendance(
                event_id=event.id, player_id=player.id, joined_at=now,
                last_sighted_at=now, rank=0, sighted_at_course=True,
            ),
            EventParticipant(
                event_id=event.id, player_id=player.id, status=ParticipantStatus.ATTENDING,
                role=ParticipantRole.RUNNER, responded_at=now,
            ),
            ParticipantStint(
                event_id=event.id, player_id=player.id, role=ParticipantRole.RUNNER,
                started_at=now,
            ),
            Lap(player_id=player.id, event_id=event.id, lap_count=1, completed_at=now),
            Death(player_id=player.id, event_id=event.id, died_at=now),
        ]
    )
    await db_session.flush()
    db_session.add(DdPresence(dd_call_id=call.id, player_id=player.id, sources=[]))
    await db_session.commit()
    return event, player


async def _count(db_session, model, **where) -> int:
    query = select(func.count()).select_from(model)
    for column, value in where.items():
        query = query.where(getattr(model, column) == value)
    return await db_session.scalar(query)


async def test_confirm_page_lists_what_will_go(
    client, make_user, login_as, default_group, db_session
):
    event, _ = await _mass_with_data(db_session, default_group)
    login_as(await make_user(UserRole.ADMIN))

    response = await client.get(f"/events/delete?ids={event.id}")
    assert response.status_code == 200
    assert event.name in response.text
    assert "Laps: 1" in response.text and "DD tile timings: 1" in response.text
    # Nothing is deleted by looking.
    assert await _count(db_session, Event, id=event.id) == 1


async def test_admin_deletes_masses_and_all_their_data(
    client, make_user, login_as, default_group, db_session
):
    doomed, player = await _mass_with_data(db_session, default_group)
    kept, kept_player = await _mass_with_data(db_session, default_group)
    doomed_id, player_id, kept_id, kept_player_id = doomed.id, player.id, kept.id, kept_player.id
    csrf = login_as(await make_user(UserRole.ADMIN))

    response = await client.post(
        "/events/delete", data={"ids": [str(doomed_id)], "csrf_token": csrf},
        follow_redirects=False,
    )
    assert response.status_code == 303

    db_session.expire_all()
    assert await _count(db_session, Event, id=doomed_id) == 0
    for model in (Attendance, EventParticipant, ParticipantStint, Lap, Death, DdCall):
        assert await _count(db_session, model, event_id=doomed_id) == 0, model
    assert await _count(db_session, DdPresence, player_id=player_id) == 0
    # The player row is shared across masses and stays.
    assert await _count(db_session, Player, id=player_id) == 1
    # Other masses are untouched.
    assert await _count(db_session, Event, id=kept_id) == 1
    assert await _count(db_session, Lap, event_id=kept_id) == 1
    assert await _count(db_session, DdPresence, player_id=kept_player_id) == 1


async def test_running_mass_cannot_be_deleted(
    client, make_user, login_as, default_group, no_active_event, db_session
):
    running, _ = await _mass_with_data(db_session, default_group, ended=False)
    running_id = running.id
    csrf = login_as(await make_user(UserRole.ADMIN))

    await client.post("/events/delete", data={"ids": [str(running_id)], "csrf_token": csrf})

    db_session.expire_all()
    assert await _count(db_session, Event, id=running_id) == 1
    assert await _count(db_session, Lap, event_id=running_id) == 1
    # Don't leave a running mass behind for later tests.
    running = await db_session.get(Event, running_id)
    running.ended_at = dt.datetime.utcnow()
    await db_session.commit()


async def test_staff_cannot_delete_masses(
    client, make_user, login_as, default_group, db_session
):
    event, _ = await _mass_with_data(db_session, default_group)
    csrf = login_as(await make_user(UserRole.STAFF))

    assert (await client.get(f"/events/delete?ids={event.id}")).status_code == 403
    response = await client.post(
        "/events/delete", data={"ids": [str(event.id)], "csrf_token": csrf}
    )
    assert response.status_code == 403
    assert await _count(db_session, Event, id=event.id) == 1


async def test_delete_needs_csrf(client, make_user, login_as, default_group, db_session):
    event, _ = await _mass_with_data(db_session, default_group)
    login_as(await make_user(UserRole.ADMIN))

    response = await client.post("/events/delete", data={"ids": [str(event.id)]})
    assert response.status_code == 403
    assert await _count(db_session, Event, id=event.id) == 1
