"""kits + gear rules; drop loadout requirements and the DD crossbow toggle

Revision ID: d2f7a8b9c014
Revises: c9e5a13f7d46
Create Date: 2026-10-27 16:00:00.000000

"""

import sqlalchemy as sa

from alembic import op

# revision identifiers, used by Alembic.
revision = "d2f7a8b9c014"
down_revision = "c9e5a13f7d46"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "kits",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("fc_group_id", sa.Integer(), sa.ForeignKey("fc_groups.id"), nullable=False),
        sa.Column("name", sa.String(64), nullable=False),
        sa.Column("is_default", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    op.create_index("ix_kits_fc_group_id", "kits", ["fc_group_id"])
    op.create_table(
        "kit_items",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column(
            "kit_id", sa.Integer(), sa.ForeignKey("kits.id", ondelete="CASCADE"), nullable=False
        ),
        sa.Column("slot", sa.String(16), nullable=False),
        sa.Column("item_id", sa.Integer(), nullable=False),
        sa.Column("qty", sa.Integer(), nullable=False, server_default="1"),
    )
    op.create_index("ix_kit_items_kit_id", "kit_items", ["kit_id"])

    op.add_column("fc_groups", sa.Column("required_slots", sa.JSON(), nullable=True))
    op.add_column(
        "fc_groups",
        sa.Column("require_ranged_weapon", sa.Boolean(), nullable=False, server_default=sa.true()),
    )
    op.drop_column("fc_groups", "dd_crossbow_counts_as_bow")
    op.drop_table("loadout_requirements")


def downgrade() -> None:
    op.create_table(
        "loadout_requirements",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("loadout_id", sa.Integer(), sa.ForeignKey("loadouts.id"), nullable=False),
        sa.Column("slot", sa.String(32), nullable=False),
        sa.Column("required", sa.Boolean(), nullable=False),
        sa.Column("allowed_item_ids", sa.JSON(), nullable=True),
        sa.UniqueConstraint("loadout_id", "slot", name="uq_loadout_requirements_slot"),
    )
    op.add_column(
        "fc_groups",
        sa.Column(
            "dd_crossbow_counts_as_bow", sa.Boolean(), nullable=False, server_default=sa.true()
        ),
    )
    op.drop_column("fc_groups", "require_ranged_weapon")
    op.drop_column("fc_groups", "required_slots")
    op.drop_index("ix_kit_items_kit_id", table_name="kit_items")
    op.drop_table("kit_items")
    op.drop_index("ix_kits_fc_group_id", table_name="kits")
    op.drop_table("kits")
