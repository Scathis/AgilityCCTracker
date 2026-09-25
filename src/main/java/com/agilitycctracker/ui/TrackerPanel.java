package com.agilitycctracker.ui;

import com.agilitycctracker.AgilityCCTrackerConfig;
import com.agilitycctracker.api.ApiClient;
import com.agilitycctracker.api.StatusHolder;
import com.agilitycctracker.collect.LapTracker;
import com.agilitycctracker.dd.DdTracker;
import com.agilitycctracker.loadout.SelfLoadoutCheck;
import com.agilitycctracker.model.ActiveEvent;
import com.agilitycctracker.model.StatusResponse;
import com.agilitycctracker.scan.FcMemberScanner;
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
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
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
	private AgilityCCTrackerConfig config;

	@Inject
	private ApiClient apiClient;

	@Inject
	private StatusHolder statusHolder;

	@Inject
	private LapTracker lapTracker;

	@Inject
	private SelfLoadoutCheck selfLoadoutCheck;

	@Inject
	private DdTracker ddTracker;

	@Inject
	private FcMemberScanner fcMemberScanner;

	private final JLabel connectionLabel = new JLabel();
	private final JLabel fcLabel = new JLabel();
	private final JLabel lapsLabel = new JLabel();
	private final JLabel loadoutLabel = new JLabel();
	private final JLabel ddLabel = new JLabel();
	private final JPanel observersList = new JPanel();

	public void init()
	{
		setLayout(new BorderLayout());

		for (JLabel label : new JLabel[] {connectionLabel, fcLabel, lapsLabel, loadoutLabel, ddLabel})
		{
			label.setForeground(ColorScheme.TEXT_COLOR);
		}

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		content.add(connectionLabel);
		content.add(fcLabel);
		content.add(lapsLabel);
		content.add(loadoutLabel);
		content.add(ddLabel);

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

		refresh();
	}

	/** Called from the plugin's 1s schedule tick. Must run on the EDT (RuneLite calls this via SwingUtilities.invokeLater). */
	public void refresh()
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

		lapsLabel.setText("Laps this session: " + lapTracker.getSessionLapCount()
			+ " (" + lapTracker.getSessionTicketsGained() + " tickets)");

		List<String> failed = selfLoadoutCheck.getLastFailed();
		loadoutLabel.setText(failed.isEmpty() ? "Loadout: compliant" : "Loadout: " + failed.size() + " gap(s)");

		ddLabel.setText(ddStatus());

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

	private String connectionStatus()
	{
		if (config.apiUrl() == null || config.apiUrl().trim().isEmpty())
		{
			return "no URL set";
		}
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

	private String ddStatus()
	{
		if (!ddTracker.isWindowOpen())
		{
			return "DD: inactive";
		}
		Player local = client.getLocalPlayer();
		String rsn = local != null ? local.getName() : null;
		long elapsedMs = System.currentTimeMillis() - ddTracker.getCalledAt();
		String elapsed = (elapsedMs / 1000) + "s";
		if (rsn != null)
		{
			var sample = ddTracker.getSample(rsn);
			long msOnTile = sample != null ? sample.getMsOnTile() : 0;
			return "DD: active " + elapsed + " - own time on tile " + (msOnTile / 1000) + "s";
		}
		return "DD: active " + elapsed;
	}

	private void copyMyTile()
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		WorldPoint point = local.getWorldLocation();
		String text = point.getX() + "," + point.getY() + "," + point.getPlane();
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PANEL_WIDTH, super.getPreferredSize().height);
	}
}
