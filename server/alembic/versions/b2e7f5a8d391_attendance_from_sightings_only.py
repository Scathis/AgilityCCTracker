"""attendance from course sightings only; drop unused location/DD-gear fields

Owner decisions 2026-10-27:
- The plugin no longer uploads the FC member list. Attendance is opened by
  course sightings alone, so attendance.world (only used by the old roster
  diff) goes, and rows for people who were in the chat but never seen at the
  course are deleted, along with any player record nothing else refers to.
- deaths.world / deaths.wildy_level: every death that matters is at the course.
- dd_presence.dd_loadout_failed: the DD gear check was removed; always empty.

Downgrade restores the columns (empty/defaulted) but not the deleted rows -
restore a backup for those.

Revision ID: b2e7f5a8d391
Revises: a1d6e4f7c290
Create Date: 2026-10-27 23:55:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "b2e7f5a8d391"
down_revision = "a1d6e4f7c290"
branch_labels = None
depends_on = None

PLAYER_REFERENCES = (
    ("attendance", "player_id"),
    ("dd_presence", "player_id"),
    ("deaths", "player_id"),
    ("event_participants", "player_id"),
    ("laps", "player_id"),
    ("loadout_checks", "player_id"),
    ("loadout_checks", "reported_by_player_id"),
    ("participant_stints", "player_id"),
)


def upgrade() -> None:
    op.execute("DELETE FROM attendance WHERE sighted_at_course = 0")
    referenced = " AND ".join(
        f"id NOT IN (SELECT {column} FROM {table} WHERE {column} IS NOT NULL)"
        for table, column in PLAYER_REFERENCES
    )
    # MySQL won't select from the table being deleted from in a subquery
    # unless it's wrapped in a derived table.
    op.execute(
        "DELETE FROM players WHERE id IN "
        f"(SELECT id FROM (SELECT id FROM players WHERE {referenced}) AS orphans)"
    )

    op.drop_column("attendance", "world")
    op.drop_column("deaths", "world")
    op.drop_column("deaths", "wildy_level")
    op.drop_column("dd_presence", "dd_loadout_failed")


def downgrade() -> None:
    op.add_column("dd_presence", sa.Column("dd_loadout_failed", sa.JSON(), nullable=True))
    op.add_column(
        "deaths", sa.Column("wildy_level", sa.Integer(), nullable=False, server_default="0")
    )
    op.add_column("deaths", sa.Column("world", sa.Integer(), nullable=False, server_default="0"))
    op.add_column(
        "attendance", sa.Column("world", sa.Integer(), nullable=False, server_default="0")
    )
