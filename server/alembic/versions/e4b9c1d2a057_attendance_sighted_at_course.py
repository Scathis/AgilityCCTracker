"""attendance.sighted_at_course: was this session ever seen at the course

Revision ID: e4b9c1d2a057
Revises: d2f7a8b9c014
Create Date: 2026-10-27 21:00:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "e4b9c1d2a057"
down_revision = "d2f7a8b9c014"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "attendance",
        sa.Column("sighted_at_course", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    # Existing rows: a course sighting is the only thing that ever moves
    # last_sighted_at off its initial value (joined_at).
    op.execute("UPDATE attendance SET sighted_at_course = 1 WHERE last_sighted_at <> joined_at")


def downgrade() -> None:
    op.drop_column("attendance", "sighted_at_course")
