"""drop ragger tracking and players' stored worn equipment

Owner decisions 2026-10-27: raggers aren't something the FC tracks (both
tables were empty), and the server must never hold what anyone is wearing -
only which required slots are empty - so the unused own-equipment column on
player_snapshots goes too. Downgrade recreates the empty structure only.

Revision ID: f5c0d2e3b168
Revises: e4b9c1d2a057
Create Date: 2026-10-27 22:30:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "f5c0d2e3b168"
down_revision = "e4b9c1d2a057"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.drop_table("ragger_sightings")
    op.drop_table("raggers")
    op.drop_column("player_snapshots", "equipment")


def downgrade() -> None:
    op.add_column("player_snapshots", sa.Column("equipment", sa.JSON(), nullable=True))
    op.create_table(
        "raggers",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("rsn", sa.String(length=12), nullable=False),
        sa.Column("status", sa.Enum("ACTIVE", "CLEARED", name="raggerstatus"), nullable=False),
        sa.Column("threat_notes", sa.Text(), nullable=True),
        sa.Column("added_by_user_id", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.Column("combat_level", sa.Integer(), nullable=True),
        sa.Column("skills", sa.JSON(), nullable=True),
        sa.Column("hiscores_updated_at", sa.DateTime(), nullable=True),
        sa.ForeignKeyConstraint(["added_by_user_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("rsn"),
    )
    op.create_table(
        "ragger_sightings",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("ragger_id", sa.Integer(), nullable=False),
        sa.Column("event_id", sa.Integer(), nullable=True),
        sa.Column("seen_at", sa.DateTime(), nullable=False),
        sa.Column("world", sa.Integer(), nullable=True),
        sa.Column("gear_notes", sa.Text(), nullable=True),
        sa.Column("reported_by_user_id", sa.Integer(), nullable=False),
        sa.ForeignKeyConstraint(["event_id"], ["events.id"]),
        sa.ForeignKeyConstraint(["ragger_id"], ["raggers.id"]),
        sa.ForeignKeyConstraint(["reported_by_user_id"], ["users.id"]),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_ragger_sightings_ragger_id", "ragger_sightings", ["ragger_id"], unique=False
    )
