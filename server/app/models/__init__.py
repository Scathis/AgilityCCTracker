"""Import every model module here so Base.metadata (and Alembic's
autogenerate) sees the full schema regardless of which module happens to
be imported first elsewhere."""

from app.models.base import Base
from app.models.dd import DdCall, DdPresence
from app.models.death import Death
from app.models.event import Attendance, Event, EventParticipant, ParticipantStint
from app.models.fc_group import FcGroup
from app.models.item import Item
from app.models.kit import Kit, KitItem
from app.models.lap import Lap
from app.models.loadout import Loadout, LoadoutCheck
from app.models.player import Player, PlayerSnapshot
from app.models.ragger import Ragger, RaggerSighting
from app.models.user import User

__all__ = [
    "Base",
    "FcGroup",
    "Player",
    "PlayerSnapshot",
    "Event",
    "Attendance",
    "EventParticipant",
    "ParticipantStint",
    "Lap",
    "Death",
    "Loadout",
    "Kit",
    "KitItem",
    "LoadoutCheck",
    "DdCall",
    "DdPresence",
    "Ragger",
    "RaggerSighting",
    "User",
    "Item",
]
