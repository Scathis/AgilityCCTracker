"""self-registration: users.status (pending/active) and users.protected

Existing accounts become ACTIVE. The owner account (grestinator) is marked
protected so it can't be removed, demoted or have its password reset from
the dashboard.

Revision ID: c3f8a6b9e402
Revises: b2e7f5a8d391
Create Date: 2026-10-28 00:30:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "c3f8a6b9e402"
down_revision = "b2e7f5a8d391"
branch_labels = None
depends_on = None

OWNER_USERNAME = "grestinator"


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column(
            "status",
            sa.Enum("PENDING", "ACTIVE", name="userstatus"),
            nullable=False,
            server_default="ACTIVE",
        ),
    )
    op.add_column(
        "users", sa.Column("protected", sa.Boolean(), nullable=False, server_default=sa.false())
    )
    op.execute(
        sa.text("UPDATE users SET protected = 1 WHERE LOWER(username) = :owner").bindparams(
            owner=OWNER_USERNAME
        )
    )


def downgrade() -> None:
    op.execute("DELETE FROM users WHERE status = 'PENDING'")
    op.drop_column("users", "protected")
    op.drop_column("users", "status")
