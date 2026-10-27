"""drop player stats: snapshots (skills, combat level) and hiscores tracking

Owner decision 2026-10-27: the FC doesn't need anyone's stats - the plugin
shows the player's own Agility level locally and nothing server-side uses
skills or combat level. The Players pages and the background OSRS hiscores
lookups that fed them are gone too. Downgrade recreates the empty structure.

Revision ID: a1d6e4f7c290
Revises: f5c0d2e3b168
Create Date: 2026-10-27 23:15:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "a1d6e4f7c290"
down_revision = "f5c0d2e3b168"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.drop_constraint("fk_player_latest_snapshot", "players", type_="foreignkey")
    op.drop_column("players", "latest_snapshot_id")
    op.drop_column("players", "hiscores_updated_at")
    op.drop_table("player_snapshots")


def downgrade() -> None:
    op.create_table(
        "player_snapshots",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("player_id", sa.Integer(), nullable=False),
        sa.Column("captured_at", sa.DateTime(), nullable=False),
        sa.Column("source", sa.Enum("PLUGIN", "HISCORES", name="snapshotsource"), nullable=False),
        sa.Column("world", sa.Integer(), nullable=True),
        sa.Column("combat_level", sa.Integer(), nullable=True),
        sa.Column("skills", sa.JSON(), nullable=True),
        sa.ForeignKeyConstraint(["player_id"], ["players.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_player_snapshots_player_id", "player_snapshots", ["player_id"], unique=False
    )
    op.add_column("players", sa.Column("hiscores_updated_at", sa.DateTime(), nullable=True))
    op.add_column("players", sa.Column("latest_snapshot_id", sa.Integer(), nullable=True))
    op.create_foreign_key(
        "fk_player_latest_snapshot", "players", "player_snapshots", ["latest_snapshot_id"], ["id"]
    )
