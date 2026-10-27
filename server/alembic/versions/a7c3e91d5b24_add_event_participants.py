"""add event_participants (opt-in + runner/anti role per mass)

Revision ID: a7c3e91d5b24
Revises: 04df333581b5
Create Date: 2026-10-27 12:00:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "a7c3e91d5b24"
down_revision = "04df333581b5"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "event_participants",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("event_id", sa.Integer(), sa.ForeignKey("events.id"), nullable=False),
        sa.Column("player_id", sa.Integer(), sa.ForeignKey("players.id"), nullable=False),
        sa.Column(
            "status", sa.Enum("ATTENDING", "DECLINED", name="participantstatus"), nullable=False
        ),
        sa.Column("role", sa.Enum("RUNNER", "ANTI", name="participantrole"), nullable=False),
        sa.Column("responded_at", sa.DateTime(), nullable=False),
        sa.UniqueConstraint("event_id", "player_id", name="uq_event_participant"),
    )
    op.create_index("ix_event_participants_event_id", "event_participants", ["event_id"])
    op.create_index("ix_event_participants_player_id", "event_participants", ["player_id"])


def downgrade() -> None:
    op.drop_index("ix_event_participants_player_id", table_name="event_participants")
    op.drop_index("ix_event_participants_event_id", table_name="event_participants")
    op.drop_table("event_participants")
