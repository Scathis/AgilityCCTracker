"""Import every model module here so Base.metadata (and Alembic's
autogenerate) sees the full schema regardless of which module happens to
be imported first elsewhere."""

from app.models.base import Base
from app.models.dd import DdCall, DdPresence
from app.models.death import Death
from app.models.event import Attendance, Event
from app.models.fc_group import FcGroup
from app.models.item import Item
from app.models.lap import Lap
from app.models.loadout import Loadout, LoadoutCheck, LoadoutRequirement
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
    "Lap",
    "Death",
    "Loadout",
    "LoadoutRequirement",
    "LoadoutCheck",
    "DdCall",
    "DdPresence",
    "Ragger",
    "RaggerSighting",
    "User",
    "Item",
]
