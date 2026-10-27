"""items.equip_slot - which slot each wearable item goes in

Filled by the items worker from RuneLite's item stats on its next run (it
runs at startup). Kit slot pickers only offer matching items.

Revision ID: e5b0c8d1a624
Revises: d4a9b7c0f513
Create Date: 2026-10-28 01:30:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "e5b0c8d1a624"
down_revision = "d4a9b7c0f513"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("items", sa.Column("equip_slot", sa.String(length=12), nullable=True))
    op.create_index("ix_items_equip_slot", "items", ["equip_slot"])


def downgrade() -> None:
    op.drop_index("ix_items_equip_slot", table_name="items")
    op.drop_column("items", "equip_slot")
