"""add participant_stints (per-role time within a mass)

Revision ID: b8d4f02e6c35
Revises: a7c3e91d5b24
Create Date: 2026-10-27 14:00:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "b8d4f02e6c35"
down_revision = "a7c3e91d5b24"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "participant_stints",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("event_id", sa.Integer(), sa.ForeignKey("events.id"), nullable=False),
        sa.Column("player_id", sa.Integer(), sa.ForeignKey("players.id"), nullable=False),
        sa.Column("role", sa.Enum("RUNNER", "ANTI", name="participantrole"), nullable=False),
        sa.Column("started_at", sa.DateTime(), nullable=False),
        sa.Column("ended_at", sa.DateTime(), nullable=True),
    )
    op.create_index("ix_participant_stints_event_id", "participant_stints", ["event_id"])
    op.create_index("ix_participant_stints_player_id", "participant_stints", ["player_id"])
    # Backfill: anyone already attending the current mass gets a stint from their answer time.
    op.execute(
        "INSERT INTO participant_stints (event_id, player_id, role, started_at) "
        "SELECT event_id, player_id, role, responded_at FROM event_participants "
        "WHERE status = 'ATTENDING'"
    )


def downgrade() -> None:
    op.drop_index("ix_participant_stints_player_id", table_name="participant_stints")
    op.drop_index("ix_participant_stints_event_id", table_name="participant_stints")
    op.drop_table("participant_stints")
