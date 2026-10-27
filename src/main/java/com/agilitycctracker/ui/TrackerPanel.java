package com.agilitycctracker.ui;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.MassRole;
import com.agilitycctracker.api.ApiClient;
import com.agilitycctracker.api.ServerConfig;
import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.collect.LapTracker;
import com.agilitycctracker.collect.SelfStateCollector;
import com.agilitycctracker.dd.DdTracker;
import com.agilitycctracker.kit.KitCheck;
import com.agilitycctracker.loadout.SelfLoadoutCheck;
import com.agilitycctracker.mass.MassParticipation;
import com.agilitycctracker.model.ActiveEvent;
import com.agilitycctracker.model.Kit;
import com.agilitycctracker.model.StatusResponse;
import com.agilitycctracker.scan.FcMemberScanner;
import com.agilitycctracker.scan.FlagAccess;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel, in the dashboard's brown-and-gold look: an Agility ring and
 * the player's name up top, then a status box (friends chat joined or not,
 * whether a mass is running, and the player's own answer or the buttons to
 * give one), then - while attending - this session's numbers, the kit check
 * and, for entitled observers, runners in view with gear gaps. Connection
 * details sit in small print at the bottom. Text only: never shows anyone's
 * coordinates or item ids except the player's own tile via "Copy my tile".
 */
@Singleton
public class TrackerPanel extends PluginPanel
{
	private static final String FALLBACK_FC_NAME = "Agility CC";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ApiClient apiClient;

	@Inject
	private StatusHolder statusHolder;

	@Inject
	private LapTracker lapTracker;

	@Inject
	private SelfStateCollector selfStateCollector;

	@Inject
	private SelfLoadoutCheck selfLoadoutCheck;

	@Inject
	private DdTracker ddTracker;

	@Inject
	private FcMemberScanner fcMemberScanner;

	@Inject
	private MassParticipation massParticipation;

	@Inject
	private KitCheck kitCheck;

	@Inject
	private ConfigManager configManager;

	@Inject
	private AgilityCCTrackerConfig config;

	// Header
	private final AgilityRing ring = new AgilityRing();
	private final JLabel nameLabel = label("", Theme.title().deriveFont(18f), Theme.GOLD_BRIGHT);
	private final JLabel xpLabel = label("", Theme.small(), Theme.MUTED);

	// Status box
	private final StatusMark fcMark = new StatusMark();
	private final JLabel fcName = label(FALLBACK_FC_NAME, Theme.body(), Theme.TEXT);
	private final JLabel fcState = label("", Theme.small(), Theme.MUTED);
	private final JLabel fcHint = label("", Theme.small(), Theme.MUTED);
	private final JLabel massValue = label("", Theme.body(), Theme.TEXT);
	private final JPanel youRow = transparent(new BorderLayout());
	private final JLabel youValue = label("", Theme.body(), Theme.TEXT);
	private final ThemedButton switchRole = new ThemedButton("", ThemedButton.Style.LINK);
	private final JPanel attendButtons = transparent(new GridLayout(1, 2, 6, 0));
	private final ThemedButton attendRunner = new ThemedButton("Runner", ThemedButton.Style.PRIMARY);
	private final ThemedButton attendAnti = new ThemedButton("Anti", ThemedButton.Style.SECONDARY);
	private final JLabel youError = label("", Theme.small(), Theme.BAD);

	// Session card (runners only)
	private final JPanel sessionCard = card();
	private final JLabel lapsValue = label("", Theme.body(), Theme.TEXT);
	private final JLabel courseValue = label("", Theme.body(), Theme.TEXT);
	private final JLabel gearValue = label("", Theme.body(), Theme.TEXT);
	private final JPanel ddRow = transparent(new BorderLayout());
	private final JLabel ddValue = label("", Theme.body(), Theme.GOLD_BRIGHT);

	// Kit card
	private final JPanel kitCard = card();
	private final DefaultComboBoxModel<Kit> kitModel = new DefaultComboBoxModel<>();
	private final JComboBox<Kit> kitBox = new JComboBox<>(kitModel);
	private final JLabel kitStatus = label("", Theme.small(), Theme.MUTED);
	private List<Long> shownKitIds = Collections.emptyList();
	private boolean updatingKits;

	// Observers card
	private final JPanel observersCard = card();
	private final JPanel observersList = transparent(new GridBagLayout());

	// Footer
	private final JLabel connectionLabel = label("", Theme.small(), Theme.MUTED);
	private final StatusMark connectionMark = new StatusMark();

	private PanelSnapshot lastSnapshot = PanelSnapshot.LOGGED_OUT;

	public void init()
	{
		// This panel is a Guice singleton, but startUp() calls init() again
		// every time the plugin is re-enabled after being toggled off - clear
		// out whatever a previous init() built first, so repeated calls are
		// safe instead of stacking duplicate content.
		removeAll();
		setLayout(new BorderLayout());
		setBackground(Theme.BACKGROUND);
		paintWrapper();

		JPanel content = transparent(new GridBagLayout());
		Stack stack = new Stack(content);
		stack.add(header(), 0);
		stack.add(new OrnamentDivider(), 6);
		stack.add(statusBox(), 6);
		stack.add(sessionCard(), 10);
		stack.add(kitCard(), 10);
		stack.add(observersCard(), 10);
		stack.add(footer(), 14);
		add(content, BorderLayout.NORTH);

		// init() runs on the client thread (called from startUp()), so these reads are safe here.
		Player local = client.getLocalPlayer();
		refresh(local != null && local.getName() != null
			? new PanelSnapshot(local.getName(), client.getSkillExperience(net.runelite.api.Skill.AGILITY), false)
			: PanelSnapshot.LOGGED_OUT);
	}

	/** PluginPanel wraps itself in a scroll pane; give that the same background so there's no grey band below. */
	private void paintWrapper()
	{
		JScrollPane scroll = getScrollPane();
		if (scroll != null)
		{
			scroll.getViewport().setBackground(Theme.BACKGROUND);
			scroll.setBackground(Theme.BACKGROUND);
			if (scroll.getViewport().getView() != null)
			{
				scroll.getViewport().getView().setBackground(Theme.BACKGROUND);
			}
		}
		if (getWrappedPanel() != null)
		{
			getWrappedPanel().setBackground(Theme.BACKGROUND);
		}
	}

	private JPanel header()
	{
		JPanel header = transparent(new BorderLayout(8, 0));
		header.add(ring, BorderLayout.WEST);

		JPanel names = transparent(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.CENTER;
		nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
		names.add(nameLabel, c);
		c.gridy = 1;
		c.insets = new Insets(2, 0, 0, 0);
		names.add(xpLabel, c);
		header.add(names, BorderLayout.CENTER);
		return header;
	}

	private JPanel statusBox()
	{
		JPanel box = card();
		Stack stack = new Stack(box);

		stack.add(caption("Friends chat"), 0);
		JPanel fcRow = transparent(new BorderLayout(6, 0));
		JPanel fcLeft = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
		fcLeft.add(fcMark);
		fcLeft.add(gap(6));
		fcLeft.add(fcName);
		fcRow.add(fcLeft, BorderLayout.WEST);
		fcRow.add(fcState, BorderLayout.EAST);
		stack.add(fcRow, 3);
		stack.add(fcHint, 3);

		stack.add(rule(), 8);
		stack.add(caption("Mass"), 8);
		stack.add(massValue, 3);

		JPanel youTop = transparent(new BorderLayout());
		youTop.add(youValue, BorderLayout.WEST);
		youTop.add(switchRole, BorderLayout.EAST);
		youRow.add(youTop, BorderLayout.NORTH);
		attendButtons.add(attendRunner);
		attendButtons.add(attendAnti);
		attendButtons.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		youRow.add(attendButtons, BorderLayout.CENTER);
		youRow.add(youError, BorderLayout.SOUTH);
		stack.add(youRow, 4);

		attendRunner.addActionListener(e -> attend(MassRole.RUNNER));
		attendAnti.addActionListener(e -> attend(MassRole.ANTI));
		switchRole.addActionListener(e ->
		{
			MassRole next = config.role() == MassRole.RUNNER ? MassRole.ANTI : MassRole.RUNNER;
			configManager.setConfiguration("agilitycctracker", "role", next);
		});
		return box;
	}

	private JPanel sessionCard()
	{
		Stack stack = new Stack(sessionCard);
		stack.add(heading("This session"), 0);
		stack.add(row("Laps", lapsValue), 6);
		stack.add(row("In course", courseValue), 3);
		stack.add(row("Gear", gearValue), 3);
		ddRow.add(label("DD", Theme.body(), Theme.MUTED), BorderLayout.WEST);
		ddRow.add(ddValue, BorderLayout.EAST);
		stack.add(ddRow, 3);
		return sessionCard;
	}

	private JPanel kitCard()
	{
		Stack stack = new Stack(kitCard);
		stack.add(heading("Kit"), 0);
		kitBox.setFont(Theme.body());
		kitBox.setBackground(Theme.RAISED);
		kitBox.setForeground(Theme.TEXT);
		kitBox.setFocusable(false);
		kitBox.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				Kit kit = (Kit) value;
				String text = kit == null ? "No kits yet" : kit.getName() + (kit.isDefault() ? "  (default)" : "");
				Component c = super.getListCellRendererComponent(list, text, index, selected, focus);
				c.setFont(Theme.body());
				return c;
			}
		});
		kitBox.addActionListener(e ->
		{
			Kit chosen = (Kit) kitBox.getSelectedItem();
			if (!updatingKits && chosen != null)
			{
				configManager.setConfiguration("agilitycctracker", "selectedKitId", chosen.getId());
			}
		});
		stack.add(kitBox, 6);
		stack.add(kitStatus, 6);
		return kitCard;
	}

	private JPanel observersCard()
	{
		Stack stack = new Stack(observersCard);
		stack.add(heading("Runners in view"), 0);
		stack.add(observersList, 6);
		return observersCard;
	}

	private JPanel footer()
	{
		JPanel footer = transparent(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.CENTER;

		JPanel line = transparent(new FlowLayout(FlowLayout.CENTER, 0, 0));
		line.add(connectionMark);
		line.add(gap(5));
		line.add(connectionLabel);
		footer.add(line, c);

		c.gridy = 1;
		c.insets = new Insets(1, 0, 0, 0);
		footer.add(label(host(), Theme.small(), Theme.LINE.brighter()), c);

		ThemedButton copyTile = new ThemedButton("Copy my tile", ThemedButton.Style.LINK);
		copyTile.setToolTipText("Copies your current x,y,plane - used to set up the DD tiles");
		copyTile.addActionListener(e -> copyMyTile());
		c.gridy = 2;
		c.insets = new Insets(3, 0, 0, 0);
		footer.add(copyTile, c);
		return footer;
	}

	/** Called from the plugin's 1s tick, on the EDT. The snapshot was read on the client thread. */
	public void refresh(PanelSnapshot snapshot)
	{
		lastSnapshot = snapshot;
		StatusResponse status = statusHolder.get();

		refreshHeader(snapshot);
		refreshStatusBox(snapshot, status);

		boolean runner = massParticipation.isRunner();
		sessionCard.setVisible(runner);
		if (runner)
		{
			refreshSession();
		}

		refreshKits(status);
		refreshObservers();
		refreshFooter();

		revalidate();
		repaint();
	}

	private void refreshHeader(PanelSnapshot snapshot)
	{
		boolean loggedIn = snapshot.getName() != null;
		nameLabel.setText(loggedIn ? displayName(snapshot.getName()) : "Not logged in");
		nameLabel.setForeground(loggedIn ? Theme.GOLD_BRIGHT : Theme.MUTED);
		ring.setXp(loggedIn ? snapshot.getAgilityXp() : -1);
		xpLabel.setText(loggedIn ? "Agility - " + shortXp(snapshot.getAgilityXp()) + " xp" : "");
	}

	private void refreshStatusBox(PanelSnapshot snapshot, StatusResponse status)
	{
		boolean loggedIn = snapshot.getName() != null;
		boolean inFc = snapshot.isInTrackedFc();
		fcName.setText(status != null && status.getFcName() != null ? status.getFcName() : FALLBACK_FC_NAME);
		fcMark.setOk(inFc);
		fcMark.setVisible(loggedIn);
		fcState.setText(!loggedIn ? "" : inFc ? "Joined" : "Not joined");
		fcState.setForeground(inFc ? Theme.GOOD : Theme.BAD);
		String owner = status != null && status.getFcOwner() != null ? status.getFcOwner() : "Agility Cc";
		fcHint.setText(wrap("Join from the chat-channel tab: <font color='#e3b341'>" + owner + "</font>"));
		fcHint.setVisible(loggedIn && !inFc);

		ActiveEvent event = status != null ? status.getActiveEvent() : null;
		if (event == null)
		{
			massValue.setText("No mass running");
			massValue.setForeground(Theme.MUTED);
			youRow.setVisible(false);
			return;
		}
		massValue.setText("Running - " + elapsed(event.getStartedAt()));
		massValue.setForeground(Theme.GOLD_BRIGHT);
		youRow.setVisible(loggedIn);

		boolean attending = massParticipation.isAttending();
		boolean sending = massParticipation.isSending();
		if (attending)
		{
			youValue.setText("Attending as " + config.role());
			youValue.setForeground(Theme.GOOD);
			MassRole other = config.role() == MassRole.RUNNER ? MassRole.ANTI : MassRole.RUNNER;
			switchRole.setText("Switch to " + other);
		}
		else if (sending)
		{
			youValue.setText("Sending...");
			youValue.setForeground(Theme.MUTED);
		}
		else if (massParticipation.hasDeclined(status))
		{
			youValue.setText("Not attending");
			youValue.setForeground(Theme.MUTED);
		}
		else
		{
			youValue.setText("Are you attending?");
			youValue.setForeground(Theme.TEXT);
		}
		switchRole.setVisible(attending);
		attendButtons.setVisible(!attending);
		attendRunner.setEnabled(!sending);
		attendAnti.setEnabled(!sending);
		String error = attending ? null : massParticipation.getLastError();
		youError.setText(error == null ? "" : error);
		youError.setVisible(error != null);
	}

	private void refreshSession()
	{
		lapsValue.setText(lapTracker.getSessionLapCount() + "  (" + lapTracker.getSessionTicketsGained() + " tickets)");

		boolean onCourse = selfStateCollector.isOnCourse();
		courseValue.setText(onCourse ? "Yes" : "No");
		courseValue.setForeground(onCourse ? Theme.GOOD : Theme.MUTED);

		List<String> failed = selfLoadoutCheck.getLastFailed();
		gearValue.setText(failed.isEmpty() ? "All good" : FlagAccess.describe(failed));
		gearValue.setForeground(failed.isEmpty() ? Theme.GOOD : Theme.BAD);

		ddRow.setVisible(ddTracker.isWindowOpen());
		if (ddTracker.isWindowOpen())
		{
			long elapsedS = (System.currentTimeMillis() - ddTracker.getCalledAt()) / 1000;
			var sample = lastSnapshot.getName() != null ? ddTracker.getSample(lastSnapshot.getName()) : null;
			long onTileS = sample != null ? sample.getMsOnTile() / 1000 : 0;
			ddValue.setText(elapsedS + "s  (you " + onTileS + "s on tile)");
		}
	}

	/** Keeps the kit dropdown in step with /status, without firing a selection change while doing so. */
	private void refreshKits(StatusResponse status)
	{
		List<Kit> kits = status != null && status.getKits() != null ? status.getKits() : Collections.emptyList();
		kitCard.setVisible(!kits.isEmpty() && config.role() == MassRole.RUNNER);
		List<Long> ids = new ArrayList<>();
		for (Kit kit : kits)
		{
			ids.add(kit.getId());
		}
		if (!ids.equals(shownKitIds))
		{
			updatingKits = true;
			try
			{
				kitModel.removeAllElements();
				for (Kit kit : kits)
				{
					kitModel.addElement(kit);
				}
				kitBox.setSelectedItem(KitCheck.selectKit(kits, config.selectedKitId()));
				shownKitIds = ids;
			}
			finally
			{
				updatingKits = false;
			}
		}

		if (!kitCheck.isActive())
		{
			kitStatus.setForeground(Theme.MUTED);
			kitStatus.setText(wrap("Checked against your bank and inventory once you're attending, until you reach the course."));
			return;
		}
		List<String> missing = kitCheck.getMissingLines();
		if (missing.isEmpty())
		{
			kitStatus.setForeground(Theme.GOOD);
			kitStatus.setText("Ready to go");
		}
		else
		{
			kitStatus.setForeground(Theme.BAD);
			kitStatus.setText(wrap("Missing:<br>" + String.join("<br>", missing)));
		}
	}

	private void refreshObservers()
	{
		Map<String, List<String>> results = fcMemberScanner.getLastResults();
		observersCard.setVisible(!results.isEmpty());
		observersList.removeAll();
		Stack stack = new Stack(observersList);
		for (Map.Entry<String, List<String>> entry : results.entrySet())
		{
			boolean ok = entry.getValue().isEmpty();
			JLabel value = label(ok ? "OK" : FlagAccess.describe(entry.getValue()), Theme.small(), ok ? Theme.GOOD : Theme.BAD);
			stack.add(row(displayName(entry.getKey()), value), 3);
		}
	}

	private void refreshFooter()
	{
		String text;
		boolean ok = false;
		if (apiClient.isTokenInvalid())
		{
			text = "Server rejected this plugin version";
		}
		else if (apiClient.isBackingOff())
		{
			text = "Server unreachable - retrying";
		}
		else if (statusHolder.get() == null)
		{
			text = "Connecting...";
		}
		else
		{
			text = "Connected";
			ok = true;
		}
		connectionLabel.setText(text);
		connectionMark.setOk(ok);
	}

	private void attend(MassRole role)
	{
		StatusResponse status = statusHolder.get();
		if (status != null && status.getActiveEvent() != null)
		{
			massParticipation.answer(status.getActiveEvent().getId(), true, role);
		}
	}

	private void copyMyTile()
	{
		// Button clicks fire on Swing's EDT, but reading the local player's
		// position is a client.* call RuneLite requires happen on the
		// client thread - hop there for the read, then back to the EDT for
		// the actual clipboard write (an AWT/Swing operation).
		clientThread.invoke(() ->
		{
			Player local = client.getLocalPlayer();
			if (local == null)
			{
				return;
			}
			WorldPoint point = local.getWorldLocation();
			String text = point.getX() + "," + point.getY() + "," + point.getPlane();
			SwingUtilities.invokeLater(() ->
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null));
		});
	}

	// --- formatting ---------------------------------------------------------

	private static String displayName(String name)
	{
		return name.replace(' ', ' ');
	}

	static String shortXp(int xp)
	{
		if (xp >= 1_000_000)
		{
			return String.format("%.1fM", xp / 1_000_000.0);
		}
		if (xp >= 10_000)
		{
			return String.format("%dk", xp / 1000);
		}
		return String.format("%,d", Math.max(xp, 0));
	}

	static String elapsed(long startedAtMs)
	{
		long minutes = Math.max(0, (System.currentTimeMillis() - startedAtMs) / 60_000);
		if (minutes < 1)
		{
			return "just started";
		}
		return minutes < 60 ? minutes + "m" : (minutes / 60) + "h " + (minutes % 60) + "m";
	}

	/** JLabel HTML only wraps when given a width; without one it widens the whole panel instead. */
	private static String wrap(String html)
	{
		return "<html><body style='width:155px'>" + html + "</body></html>";
	}

	private static String host()
	{
		try
		{
			String host = URI.create(ServerConfig.baseUrl()).getHost();
			return host != null ? host : ServerConfig.baseUrl();
		}
		catch (IllegalArgumentException e)
		{
			return ServerConfig.baseUrl();
		}
	}

	// --- building blocks ----------------------------------------------------

	private static JLabel label(String text, Font font, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(color);
		return label;
	}

	private static JLabel caption(String text)
	{
		return label(text, Theme.small(), Theme.MUTED);
	}

	private static JLabel heading(String text)
	{
		return label(text, Theme.title(), Theme.GOLD);
	}

	private static JPanel row(String key, JLabel value)
	{
		JPanel row = transparent(new BorderLayout(8, 0));
		row.add(label(key, Theme.body(), Theme.MUTED), BorderLayout.WEST);
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(value, BorderLayout.EAST);
		return row;
	}

	private static JPanel rule()
	{
		JPanel rule = new JPanel();
		rule.setBackground(Theme.LINE);
		rule.setPreferredSize(new Dimension(1, 1));
		return rule;
	}

	private static Component gap(int width)
	{
		JPanel gap = transparent(null);
		gap.setPreferredSize(new Dimension(width, 1));
		return gap;
	}

	private static JPanel transparent(java.awt.LayoutManager layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setOpaque(false);
		return panel;
	}

	private static JPanel card()
	{
		JPanel card = new JPanel(new GridBagLayout());
		card.setBackground(Theme.CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Theme.CARD_BORDER),
			BorderFactory.createEmptyBorder(8, 10, 9, 10)));
		return card;
	}

	/** Adds components top to bottom in a GridBagLayout panel, each stretched to full width. */
	private static final class Stack
	{
		private final JPanel panel;
		private int y;

		Stack(JPanel panel)
		{
			this.panel = panel;
		}

		void add(Component component, int topGap)
		{
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = 0;
			c.gridy = y++;
			c.weightx = 1;
			c.fill = GridBagConstraints.HORIZONTAL;
			c.insets = new Insets(y == 1 ? 0 : topGap, 0, 0, 0);
			panel.add(component, c);
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PANEL_WIDTH, super.getPreferredSize().height);
	}
}
