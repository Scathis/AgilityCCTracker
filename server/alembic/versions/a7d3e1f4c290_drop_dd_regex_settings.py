"""drop fc_groups.dd_call_regex / dd_break_regex - the DD patterns live in code now

Revision ID: a7d3e1f4c290
Revises: f6c1d9e2b735
Create Date: 2026-10-28 12:00:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "a7d3e1f4c290"
down_revision = "f6c1d9e2b735"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.drop_column("fc_groups", "dd_call_regex")
    op.drop_column("fc_groups", "dd_break_regex")


def downgrade() -> None:
    op.add_column("fc_groups", sa.Column("dd_break_regex", sa.String(255), nullable=True))
    op.add_column("fc_groups", sa.Column("dd_call_regex", sa.String(255), nullable=True))
