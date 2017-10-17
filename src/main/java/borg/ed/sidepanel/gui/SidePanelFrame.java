package borg.ed.sidepanel.gui;

import borg.ed.sidepanel.commander.CommanderData;
import borg.ed.universe.data.Coord;
import borg.ed.universe.eddn.EddnReaderThread;
import borg.ed.universe.eddn.EddnUpdateListener;
import borg.ed.universe.journal.JournalReaderThread;
import borg.ed.universe.journal.JournalUpdateListener;
import borg.ed.universe.journal.events.AbstractJournalEvent;
import borg.ed.universe.journal.events.FSDJumpEvent;
import borg.ed.universe.journal.events.ScanEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;

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
    private final CommanderData commanderData;

    private final ExecutorService delayedEsUpdateThreadPool = Executors.newFixedThreadPool(1);

    private StatusPanel statusPanel = null;
    private DiscoveryPanel discoveryPanel = null;
    private JTabbedPane tabbedPane = null;

    public SidePanelFrame(String title, ApplicationContext appctx, CommanderData commanderData) throws HeadlessException {
        super(title);

        this.journalReaderThread = appctx.getBean(JournalReaderThread.class);
        this.eddnReaderThread = appctx.getBean(EddnReaderThread.class);
        this.commanderData = commanderData;

        this.statusPanel = new StatusPanel();
        this.discoveryPanel = new DiscoveryPanel(appctx, commanderData);
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
        this.eddnReaderThread.addListener(this);
        this.eddnReaderThread.start();
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
        this.commanderData.updateFromJournalEvent(event);

        this.statusPanel.updateFromCommanderData(this.commanderData);
    }

    @Override
    public void onNewJournalMessage(ZonedDateTime gatewayTimestamp, String uploaderID, AbstractJournalEvent event) {
        if (event != null) {
            Coord currentCoord = this.commanderData.getCurrentCoord();
            Coord eventCoord = null;

            if (event instanceof FSDJumpEvent) {
                eventCoord = ((FSDJumpEvent) event).getStarPos();
            } else if (event instanceof ScanEvent) {
                eventCoord = ((ScanEvent) event).getStarPos();
            }

            if (currentCoord != null && eventCoord != null && currentCoord.distanceTo(eventCoord) <= 1000) {
                try {
                    logger.info("Event at " + eventCoord + " -- Ly distance: " + currentCoord.distanceTo(eventCoord));
                    this.delayedEsUpdateThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(2500L);
                                SidePanelFrame.this.discoveryPanel.updateFromElasticsearch();
                            } catch (InterruptedException e) {
                                // Quit
                            }
                        }
                    });
                } catch (Exception e) {
                    logger.error("Failed to update DiscoveryPanel", e);
                }
            }
        }
    }

}
