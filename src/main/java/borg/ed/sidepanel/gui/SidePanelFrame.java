package borg.ed.sidepanel.gui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import borg.ed.galaxy.data.Coord;
import borg.ed.galaxy.eddn.EddnBufferThread;
import borg.ed.galaxy.eddn.EddnReaderThread;
import borg.ed.galaxy.eddn.EddnUpdateListener;
import borg.ed.galaxy.journal.JournalReaderThread;
import borg.ed.galaxy.journal.JournalUpdateListener;
import borg.ed.galaxy.journal.events.AbstractJournalEvent;
import borg.ed.galaxy.journal.events.DockedEvent;
import borg.ed.galaxy.journal.events.FSDJumpEvent;
import borg.ed.galaxy.journal.events.LocationEvent;
import borg.ed.galaxy.journal.events.ScanEvent;
import borg.ed.sidepanel.SidepanelApplication;
import borg.ed.sidepanel.commander.CommanderData;
import borg.ed.sidepanel.commander.OtherCommanderLocation;

/**
 * SidePanelFrame
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
public class SidePanelFrame extends JFrame implements WindowListener, JournalUpdateListener, EddnUpdateListener {

	private static final long serialVersionUID = 3035677791969632318L;

	static final Logger logger = LoggerFactory.getLogger(SidePanelFrame.class);

	private final JournalReaderThread journalReaderThread;
	private final EddnReaderThread eddnReaderThread;
	private final EddnBufferThread eddnBufferThread;
	private final CommanderData commanderData;
	private final Map<String, OtherCommanderLocation> otherCommanders;

	private final ExecutorService delayedEsUpdateThreadPool = Executors.newFixedThreadPool(1);
	private long lastDiscoveryPanelUpdate = 0L;

	private StatusPanel statusPanel = null;
	private DiscoveryPanel discoveryPanel = null;
	private JTabbedPane tabbedPane = null;

	public SidePanelFrame(String title, ApplicationContext appctx, CommanderData commanderData, Map<String, OtherCommanderLocation> otherCommanders) throws HeadlessException {
		super(title);

		this.journalReaderThread = appctx.getBean(JournalReaderThread.class);
		this.eddnReaderThread = appctx.getBean(EddnReaderThread.class);
		this.eddnBufferThread = appctx.getBean(EddnBufferThread.class);
		this.commanderData = commanderData;
		this.otherCommanders = otherCommanders;

		this.statusPanel = new StatusPanel();
		this.discoveryPanel = new DiscoveryPanel(appctx, commanderData, otherCommanders);
		this.tabbedPane = new JTabbedPane();
		this.tabbedPane.setFont(new Font("Sans Serif", Font.BOLD, 18));
		this.tabbedPane.addTab("Discovery", this.discoveryPanel);

		this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		this.addWindowListener(this);
		this.setLayout(new BorderLayout());
		this.add(statusPanel, BorderLayout.NORTH);
		this.add(tabbedPane, BorderLayout.CENTER);
		this.setSize(1800, 900);
		this.setLocation(10, 10);
	}

	@Override
	public void windowOpened(WindowEvent e) {
		this.journalReaderThread.addListener(this);
		this.journalReaderThread.start();
		this.eddnBufferThread.addListener(this);
		this.eddnBufferThread.start();
		this.eddnReaderThread.start();

		this.statusPanel.updateFromCommanderData(this.commanderData);
		this.discoveryPanel.updateFromElasticsearch(/* repaintMap = */ true);
	}

	@Override
	public void windowClosing(WindowEvent e) {
		this.journalReaderThread.interrupt();
		eddnReaderThread.interrupt();
	}

	@Override
	public void windowClosed(WindowEvent e) {
		while (this.journalReaderThread.isAlive()) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException ex) {
				break;
			}
		}
		while (eddnReaderThread.isAlive()) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException ex) {
				break;
			}
		}

		System.exit(0);
	}

	@Override
	public void windowIconified(WindowEvent e) {
		// Do nothing
	}

	@Override
	public void windowDeiconified(WindowEvent e) {
		// Do nothing
	}

	@Override
	public void windowActivated(WindowEvent e) {
		// Do nothing
	}

	@Override
	public void windowDeactivated(WindowEvent e) {
		// Do nothing
	}

	@Override
	public void onNewJournalEntry(AbstractJournalEvent event) {
		if (this.commanderData.updateFromJournalEvent(event)) {

			this.statusPanel.updateFromCommanderData(this.commanderData);

			if (event instanceof FSDJumpEvent) {
				this.discoveryPanel.updateFromElasticsearch(/* repaintMap = */ true);
			}
		}
	}

	@Override
	public void onNewJournalMessage(ZonedDateTime gatewayTimestamp, String uploaderID, AbstractJournalEvent event) {
		if (event != null) {
			Coord currentCoord = this.commanderData.getCurrentCoord();
			Coord eventCoord = null;

			if (event instanceof FSDJumpEvent) {
				FSDJumpEvent fsdJumpEvent = (FSDJumpEvent) event;
				eventCoord = fsdJumpEvent.getStarPos();
				this.updateOtherCommanders(uploaderID, event.getTimestamp(), fsdJumpEvent.getStarPos(), fsdJumpEvent.getStarSystem());
			} else if (event instanceof ScanEvent) {
				ScanEvent scanEvent = (ScanEvent) event;
				eventCoord = scanEvent.getStarPos();
				this.updateOtherCommanders(uploaderID, event.getTimestamp(), scanEvent.getStarPos(), scanEvent.getStarSystem());
			} else if (event instanceof DockedEvent) {
				DockedEvent dockedEvent = (DockedEvent) event;
				eventCoord = dockedEvent.getStarPos();
				this.updateOtherCommanders(uploaderID, event.getTimestamp(), dockedEvent.getStarPos(), dockedEvent.getStarSystem());
			} else if (event instanceof LocationEvent) {
				LocationEvent locationEvent = (LocationEvent) event;
				eventCoord = locationEvent.getStarPos();
				this.updateOtherCommanders(uploaderID, event.getTimestamp(), locationEvent.getStarPos(), locationEvent.getStarSystem());
			}

			if (currentCoord != null && eventCoord != null && currentCoord.distanceTo(eventCoord) <= this.discoveryPanel.getVisibleDistance()) {
				logger.info("Event at " + eventCoord + " -- Ly distance: " + currentCoord.distanceTo(eventCoord));
				this.updateDiscoveryPanelDelayed();
			}
		}
	}

	private void updateOtherCommanders(String commanderName, ZonedDateTime timestamp, Coord coord, String starSystemName) {
		try {
			if (StringUtils.isNotEmpty(commanderName) && !SidepanelApplication.MY_COMMANDER_NAME.equals(commanderName)) {
				OtherCommanderLocation location = new OtherCommanderLocation();
				location.setCommanderName(commanderName);
				location.setStarSystemName(starSystemName);
				location.setCoord(coord);
				location.setTimestamp(timestamp);
				this.otherCommanders.put(commanderName, location);

				Set<String> entriesToRemove = new HashSet<>();
				ZonedDateTime halfHourAgo = ZonedDateTime.now().minusMinutes(30);
				for (String name : this.otherCommanders.keySet()) {
					if (this.otherCommanders.get(name).getTimestamp().isBefore(halfHourAgo)) {
						entriesToRemove.add(name);
					}
				}
				if (!entriesToRemove.isEmpty()) {
					this.otherCommanders.keySet().removeAll(entriesToRemove);
				}
			}
		} catch (Exception e) {
			logger.error("Failed to update DiscoveryPanel", e);
		}
	}

	private void updateDiscoveryPanelDelayed() {
		try {
			long now = System.currentTimeMillis();
			if (now - this.lastDiscoveryPanelUpdate >= 10000L) {
				this.lastDiscoveryPanelUpdate = now;
				this.delayedEsUpdateThreadPool.execute(new Runnable() {
					@Override
					public void run() {
						try {
							Thread.sleep(5000L);
							SidePanelFrame.this.discoveryPanel.updateFromElasticsearch(/* repaintMap = */ false);
						} catch (InterruptedException e) {
							// Quit
						}
					}
				});
			}
		} catch (Exception e) {
			logger.error("Failed to update DiscoveryPanel", e);
		}
	}

}
