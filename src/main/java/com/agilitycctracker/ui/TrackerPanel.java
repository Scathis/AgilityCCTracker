package com.agilitycctracker.ui;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.api.ApiClient;
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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel (BUILD_PLAN.md §5.9): connection status, FC/event, own
 * laps/tickets this session, own loadout status, DD status, and - for
 * observers - the FC members currently in view with their gap labels. All
 * text-only; never renders coordinates or item ids for anyone but the local
 * player's own tile (via "Copy my tile", an explicit user action).
 */
@Singleton
public class TrackerPanel extends PluginPanel
{
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

	private final JLabel connectionLabel = new JLabel();
	private final JLabel fcLabel = new JLabel();
	private final JLabel massLabel = new JLabel();
	private final JButton attendButton = new JButton("Attend this mass");
	private final JLabel courseLabel = new JLabel();
	private final JLabel lapsLabel = new JLabel();
	private final JLabel loadoutLabel = new JLabel();
	private final JLabel ddLabel = new JLabel();
	private final JPanel observersList = new JPanel();
	private final DefaultComboBoxModel<Kit> kitModel = new DefaultComboBoxModel<>();
	private final JComboBox<Kit> kitBox = new JComboBox<>(kitModel);
	private final JLabel kitCheckLabel = new JLabel();
	private List<Long> shownKitIds = java.util.Collections.emptyList();
	private boolean updatingKits;

	public void init()
	{
		// This panel is a Guice singleton, but startUp() calls init() again
		// every time the plugin is re-enabled after being toggled off - clear
		// out whatever a previous init() built first, so repeated calls are
		// safe instead of stacking duplicate content panels on top of each
		// other (BorderLayout doesn't remove a slot's old occupant when a
		// new one is added to the same slot, it just stops laying it out).
		removeAll();
		setLayout(new BorderLayout());

		for (JLabel label : new JLabel[] {connectionLabel, fcLabel, massLabel, courseLabel, lapsLabel, loadoutLabel, ddLabel})
		{
			label.setForeground(ColorScheme.TEXT_COLOR);
		}

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		content.add(connectionLabel);
		content.add(fcLabel);
		content.add(massLabel);
		attendButton.addActionListener(e -> massParticipation.promptNow(statusHolder.get()));
		JPanel attendRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		attendRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		attendRow.add(attendButton);
		content.add(attendRow);
		content.add(courseLabel);
		content.add(lapsLabel);
		content.add(loadoutLabel);
		content.add(ddLabel);

		JLabel kitHeading = new JLabel("My kit:");
		kitHeading.setForeground(ColorScheme.TEXT_COLOR);
		content.add(kitHeading);
		kitBox.setRenderer(new javax.swing.DefaultListCellRenderer()
		{
			@Override
			public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean selected, boolean focus)
			{
				Kit kit = (Kit) value;
				String text = kit == null ? "(no kits yet)" : kit.getName() + (kit.isDefault() ? " (default)" : "");
				return super.getListCellRendererComponent(list, text, index, selected, focus);
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
		content.add(kitBox);
		kitCheckLabel.setForeground(ColorScheme.TEXT_COLOR);
		content.add(kitCheckLabel);

		JButton copyTileButton = new JButton("Copy my tile");
		copyTileButton.addActionListener(e -> copyMyTile());
		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
		buttonRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttonRow.add(copyTileButton);
		content.add(buttonRow);

		observersList.setLayout(new BoxLayout(observersList, BoxLayout.Y_AXIS));
		observersList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JLabel observersHeading = new JLabel("FC members in view:");
		observersHeading.setForeground(ColorScheme.TEXT_COLOR);
		content.add(observersHeading);
		content.add(observersList);

		add(content, BorderLayout.NORTH);

		// init() runs on the client thread (called from startUp()), so this
		// one read is safe directly - every other refresh() call goes
		// through onSecondTick(), which passes localRsn in instead.
		Player local = client.getLocalPlayer();
		refresh(local != null ? local.getName() : null);
	}

	/** Called from the plugin's 1s schedule tick. Must run on the EDT (RuneLite calls this via SwingUtilities.invokeLater).
	 * localRsn is read by the caller on the client thread beforehand (never touched here directly - see ddStatus()). */
	public void refresh(String localRsn)
	{
		connectionLabel.setText("Connection: " + connectionStatus());

		StatusResponse status = statusHolder.get();
		if (status == null)
		{
			fcLabel.setText("FC: (no status yet)");
		}
		else
		{
			ActiveEvent event = status.getActiveEvent();
			String eventText = event != null ? event.getName() : "no active event";
			fcLabel.setText("FC: " + status.getFcName() + " - " + eventText);
		}

		massLabel.setText("Mass: " + massParticipation.describe(status));
		attendButton.setVisible(status != null && status.getActiveEvent() != null && !massParticipation.isAttending());

		boolean onCourse = selfStateCollector.isOnCourse();
		courseLabel.setText(onCourse ? "In course area: yes" : "In course area: no");
		courseLabel.setForeground(onCourse ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.TEXT_COLOR);

		lapsLabel.setText("Laps this session: " + lapTracker.getSessionLapCount()
			+ " (" + lapTracker.getSessionTicketsGained() + " tickets)");

		List<String> failed = selfLoadoutCheck.getLastFailed();
		loadoutLabel.setText(failed.isEmpty() ? "Gear: OK" : "Gear: " + FlagAccess.describe(failed));

		refreshKits(status);

		ddLabel.setText(ddStatus(localRsn));

		observersList.removeAll();
		for (Map.Entry<String, List<String>> entry : fcMemberScanner.getLastResults().entrySet())
		{
			String text = entry.getValue().isEmpty()
				? entry.getKey() + ": compliant"
				: entry.getKey() + ": " + String.join(", ", entry.getValue());
			JLabel observerLabel = new JLabel(text);
			observerLabel.setForeground(ColorScheme.TEXT_COLOR);
			observersList.add(observerLabel);
		}
		observersList.revalidate();
		observersList.repaint();
	}

	/** Keeps the kit dropdown in step with /status, without firing a selection change while doing so. */
	private void refreshKits(StatusResponse status)
	{
		List<Kit> kits = status != null && status.getKits() != null ? status.getKits() : java.util.Collections.emptyList();
		List<Long> ids = new java.util.ArrayList<>();
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
				Kit selected = KitCheck.selectKit(kits, config.selectedKitId());
				kitBox.setSelectedItem(selected);
				shownKitIds = ids;
			}
			finally
			{
				updatingKits = false;
			}
		}
		kitBox.setEnabled(!kits.isEmpty());

		if (!kitCheck.isActive())
		{
			kitCheckLabel.setText(kits.isEmpty() ? "" : "<html>Kit check runs once you're attending as a runner, until you reach the course.</html>");
			return;
		}
		List<String> missing = kitCheck.getMissingLines();
		kitCheckLabel.setText(missing.isEmpty()
			? "<html>Kit check: all set</html>"
			: "<html>Kit check - missing:<br>" + String.join("<br>", missing) + "</html>");
	}

	private String connectionStatus()
	{
		if (apiClient.isTokenInvalid())
		{
			return "invalid token";
		}
		if (apiClient.isBackingOff())
		{
			return "backing off";
		}
		return "OK";
	}

	private String ddStatus(String localRsn)
	{
		if (!ddTracker.isWindowOpen())
		{
			return "DD: inactive";
		}
		long elapsedMs = System.currentTimeMillis() - ddTracker.getCalledAt();
		String elapsed = (elapsedMs / 1000) + "s";
		if (localRsn != null)
		{
			var sample = ddTracker.getSample(localRsn);
			long msOnTile = sample != null ? sample.getMsOnTile() : 0;
			return "DD: active " + elapsed + " - own time on tile " + (msOnTile / 1000) + "s";
		}
		return "DD: active " + elapsed;
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

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PANEL_WIDTH, super.getPreferredSize().height);
	}
}
