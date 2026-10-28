"""events.mass_bank_called_at - staff-called mass bank alert

Revision ID: f6c1d9e2b735
Revises: e5b0c8d1a624
Create Date: 2026-10-28 02:30:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "f6c1d9e2b735"
down_revision = "e5b0c8d1a624"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("events", sa.Column("mass_bank_called_at", sa.DateTime(), nullable=True))


def downgrade() -> None:
    op.drop_column("events", "mass_bank_called_at")
