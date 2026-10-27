"""add fc_groups.flag_viewer_min_rank

Revision ID: c9e5a13f7d46
Revises: b8d4f02e6c35
Create Date: 2026-10-27 15:00:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "c9e5a13f7d46"
down_revision = "b8d4f02e6c35"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "fc_groups",
        sa.Column("flag_viewer_min_rank", sa.Integer(), nullable=False, server_default="0"),
    )


def downgrade() -> None:
    op.drop_column("fc_groups", "flag_viewer_min_rank")
