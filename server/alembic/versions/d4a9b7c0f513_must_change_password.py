"""users.must_change_password - set by an admin password reset

Revision ID: d4a9b7c0f513
Revises: c3f8a6b9e402
Create Date: 2026-10-28 01:00:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "d4a9b7c0f513"
down_revision = "c3f8a6b9e402"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("must_change_password", sa.Boolean(), nullable=False, server_default=sa.false()),
    )


def downgrade() -> None:
    op.drop_column("users", "must_change_password")
