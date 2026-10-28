package com.agilitycctracker.ui;

import com.agilitycctracker.goal.AgilityGoal;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Consumer;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

/**
 * The sidebar's "Agility goal" card, the in-game version of the OSRS Hub
 * Agility Tracker: pick a goal (level 52-99 or 200m XP) and it shows laps to
 * go if every ticket is handed in at the end, and what handing in the stored
 * tickets (inventory + bank) is worth right now. Everything is computed and
 * kept locally; nothing here is sent anywhere.
 */
final class GoalCard
{
	private final JPanel card = TrackerPanel.card();
	private final JComboBox<String> goalBox = new JComboBox<>(AgilityGoal.goals().toArray(new String[0]));
	private final Bar bar = new Bar();
	private final JLabel barCaption = TrackerPanel.label("", Theme.small(), Theme.MUTED);
	private final JLabel ticketsValue = TrackerPanel.label("", Theme.body(), Theme.TEXT);
	private final JLabel ticketsNote = TrackerPanel.label("", Theme.small(), Theme.MUTED);
	private final JLabel handInValue = TrackerPanel.label("", Theme.body(), Theme.GOLD_BRIGHT);
	private final JLabel afterKey = TrackerPanel.label("", Theme.body(), Theme.MUTED);
	private final JLabel afterValue = TrackerPanel.label("", Theme.body(), Theme.TEXT);
	private final JLabel xpToGoValue = TrackerPanel.label("", Theme.body(), Theme.TEXT);
	private final JLabel lapsValue = TrackerPanel.label("", Theme.body(), Theme.GOLD_BRIGHT);
	private final JLabel timeValue = TrackerPanel.label("", Theme.body(), Theme.TEXT);
	private final JLabel ticketsAtGoalValue = TrackerPanel.label("", Theme.body(), Theme.TEXT);
	private final JLabel done = TrackerPanel.label("", Theme.body(), Theme.GOOD);
	private final JPanel toGo = TrackerPanel.transparent(new java.awt.GridBagLayout());
	private boolean updating;

	GoalCard(Consumer<String> onGoalPicked)
	{
		TrackerPanel.Stack stack = new TrackerPanel.Stack(card);
		JPanel top = TrackerPanel.transparent(new BorderLayout(8, 0));
		top.add(TrackerPanel.heading("Agility goal"), BorderLayout.WEST);
		goalBox.setFont(Theme.body());
		goalBox.setBackground(Theme.RAISED);
		goalBox.setForeground(Theme.TEXT);
		goalBox.setFocusable(false);
		goalBox.setMaximumRowCount(12);
		goalBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				Component c = super.getListCellRendererComponent(list,
					value == null ? "" : AgilityGoal.goalLabel((String) value), index, selected, focus);
				c.setFont(Theme.body());
				return c;
			}
		});
		goalBox.addActionListener(e ->
		{
			if (!updating && goalBox.getSelectedItem() != null)
			{
				onGoalPicked.accept((String) goalBox.getSelectedItem());
			}
		});
		top.add(goalBox, BorderLayout.EAST);
		stack.add(top, 0);
		stack.add(bar, 8);
		stack.add(barCaption, 3);

		stack.add(TrackerPanel.row("Tickets", ticketsValue), 8);
		stack.add(ticketsNote, 1);
		stack.add(TrackerPanel.row("Hand in now", handInValue), 3);
		JPanel after = TrackerPanel.transparent(new BorderLayout(8, 0));
		after.add(afterKey, BorderLayout.WEST);
		afterValue.setHorizontalAlignment(JLabel.RIGHT);
		after.add(afterValue, BorderLayout.EAST);
		stack.add(after, 3);

		TrackerPanel.Stack toGoStack = new TrackerPanel.Stack(toGo);
		toGoStack.add(TrackerPanel.row("XP to go", xpToGoValue), 0);
		toGoStack.add(TrackerPanel.row("Laps to goal", lapsValue), 3);
		toGoStack.add(TrackerPanel.row("Time at 45s a lap", timeValue), 3);
		toGoStack.add(TrackerPanel.row("Tickets at goal", ticketsAtGoalValue), 3);
		stack.add(toGo, 8);
		stack.add(done, 8);
	}

	JPanel getCard()
	{
		return card;
	}

	/**
	 * @param xp current Agility xp
	 * @param inventoryTickets tickets carried
	 * @param bankTickets tickets banked when the bank was last opened, or null if never
	 * @param goal the saved goal ("52".."99" or "200m")
	 */
	void refresh(int xp, int inventoryTickets, Integer bankTickets, String goal)
	{
		if (!goal.equals(goalBox.getSelectedItem()))
		{
			updating = true;
			try
			{
				goalBox.setSelectedItem(goal);
			}
			finally
			{
				updating = false;
			}
		}

		long target = AgilityGoal.targetXp(goal);
		bar.setFraction(Math.min(1.0, xp / (double) target));
		barCaption.setText(String.format("%,d / %,d xp", xp, target));

		int tickets = inventoryTickets + (bankTickets != null ? bankTickets : 0);
		ticketsValue.setText(String.format("%,d", tickets));
		ticketsNote.setText(bankTickets == null
			? TrackerPanel.wrap("Carried only - open a bank to add banked tickets.")
			: String.format("%,d banked + %,d carried", bankTickets, inventoryTickets));

		long gain = AgilityGoal.ticketXp(tickets);
		long xpAfter = Math.min(AgilityGoal.MAX_XP, xp + gain);
		handInValue.setText(tickets > 0 ? String.format("+%,d xp", gain) : "-");
		if (AgilityGoal.MAX_GOAL.equals(goal))
		{
			afterKey.setText("XP after");
			afterValue.setText(TrackerPanel.shortXp((int) xpAfter));
		}
		else
		{
			afterKey.setText("Level after");
			afterValue.setText(String.valueOf(AgilityGoal.levelForXp(xpAfter)));
		}

		boolean reached = xp >= target;
		toGo.setVisible(!reached);
		done.setVisible(reached);
		if (reached)
		{
			done.setText("Reached " + AgilityGoal.goalLabel(goal) + " - nice work.");
			return;
		}
		int laps = AgilityGoal.lapsToGoal(xp, tickets, target);
		xpToGoValue.setText(String.format("%,d", target - xp));
		lapsValue.setText(laps == 0 ? "0 - just hand in" : String.format("%,d", laps));
		timeValue.setText(hours(laps));
		ticketsAtGoalValue.setText(String.format("%,d", tickets + laps));
	}

	/** Rough time for {@code laps} at the OSRS Hub tracker's 45s a lap. */
	static String hours(int laps)
	{
		double h = laps * (double) AgilityGoal.LAP_SECONDS / 3600;
		if (laps == 0)
		{
			return "-";
		}
		return h < 1 ? "<1h" : h < 100 ? String.format("~%.1fh", h) : String.format("~%,dh", Math.round(h));
	}

	/** Thin progress bar in the panel palette. */
	private static final class Bar extends JComponent
	{
		private double fraction;

		void setFraction(double fraction)
		{
			if (fraction != this.fraction)
			{
				this.fraction = fraction;
				repaint();
			}
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(10, 8);
		}

		/** GridBagLayout falls back to minimum sizes when squeezed; without this the bar collapses to nothing. */
		@Override
		public Dimension getMinimumSize()
		{
			return getPreferredSize();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			g.setColor(Theme.RAISED);
			g.fillRoundRect(0, 0, w - 1, h - 1, h, h);
			int fill = (int) Math.round((w - 1) * fraction);
			if (fill > 0)
			{
				g.setColor(Theme.GOLD_BRIGHT);
				g.fillRoundRect(0, 0, Math.max(fill, h), h - 1, h, h);
			}
			g.setColor(Theme.CARD_BORDER);
			g.drawRoundRect(0, 0, w - 1, h - 1, h, h);
			g.dispose();
		}
	}
}
