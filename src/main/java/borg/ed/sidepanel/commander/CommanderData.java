package borg.ed.sidepanel.commander;

import borg.ed.universe.data.Coord;
import borg.ed.universe.journal.JournalEventReader;
import borg.ed.universe.journal.events.AbstractJournalEvent;
import borg.ed.universe.journal.events.FSDJumpEvent;
import borg.ed.universe.journal.events.FSDJumpEvent.Faction;
import borg.ed.universe.journal.events.LoadGameEvent;
import borg.ed.universe.journal.events.LoadoutEvent;
import borg.ed.universe.journal.events.ScanEvent;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * CommanderData
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
@Getter
@Setter
public class CommanderData {

    static final Logger logger = LoggerFactory.getLogger(CommanderData.class);

    private final String commanderName;

    private final File journalDir;

    private Coord currentCoord = null;

    private String currentStarSystem = null;

    private String currentBody = null;

    private String systemFaction = null;

    private String systemAllegiance = null;

    private String systemEconomy = null;

    private String systemState = null;

    private String systemGovernment = null;

    private String systemSecurity = null;

    private Ship currentShip = null;

    private LinkedList<VisitedStarSystem> visitedStarSystems = new LinkedList<>();

    private LinkedList<ScannedBody> scannedBodies = new LinkedList<>();

    public CommanderData(String commanderName, File journalDir) throws IOException {
        this.commanderName = commanderName;
        this.journalDir = journalDir;

        this.init();
    }

    private void init() throws IOException {
        File[] journalFiles = this.getJournalDir().listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().startsWith("Journal.") && pathname.getName().endsWith(".log");
            }
        });
        Arrays.sort(journalFiles, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                int startIdx1 = f1.getName().indexOf(".") + 1;
                int endIdx1 = f1.getName().indexOf(".", startIdx1);
                long ts1 = Long.valueOf(f1.getName().substring(startIdx1, endIdx1));

                int startIdx2 = f2.getName().indexOf(".") + 1;
                int endIdx2 = f2.getName().indexOf(".", startIdx2);
                long ts2 = Long.valueOf(f2.getName().substring(startIdx2, endIdx2));

                return new Long(ts1).compareTo(new Long(ts2));
            }
        });
        for (File journalFile : journalFiles) {
            this.initFromJournalFile(journalFile);
        }
    }

    private void initFromJournalFile(File journalFile) throws IOException {
        JournalEventReader reader = new JournalEventReader();
        List<String> lines = FileUtils.readLines(journalFile, "UTF-8");

        String currentCommander = null;

        for (int lineNo = 1; lineNo <= lines.size(); lineNo++) {
            String line = lines.get(lineNo - 1);
            try {
                AbstractJournalEvent event = reader.readLine(line);

                if (event != null) {
                    if (event instanceof LoadGameEvent) {
                        currentCommander = ((LoadGameEvent) event).getCommander();
                    }

                    if (this.getCommanderName().equals(currentCommander)) {
                        this.updateFromJournalEvent(event);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to process line " + lineNo + " of " + journalFile + "\n\t" + line, e);
            }
        }
    }

    public void updateFromJournalEvent(AbstractJournalEvent event) {
        if (event instanceof FSDJumpEvent) {
            this.updateFromFsdJumpEvent((FSDJumpEvent) event);
        } else if (event instanceof ScanEvent) {
            this.updateFromScanEvent((ScanEvent) event);
        } else if (event instanceof LoadGameEvent) {
            this.updateFromLoadGameEvent((LoadGameEvent) event);
        } else if (event instanceof LoadoutEvent) {
            this.updateFromLoadoutEvent((LoadoutEvent) event);
        }
    }

    private void updateFromFsdJumpEvent(FSDJumpEvent event) {
        this.setCurrentCoord(event.getStarPos());
        this.setCurrentStarSystem(event.getStarSystem());
        this.setCurrentBody(null);
        this.setSystemFaction(event.getSystemFaction());
        this.setSystemAllegiance(event.getSystemAllegiance());
        this.setSystemEconomy(event.getSystemEconomy());
        this.setSystemState(null);
        if (StringUtils.isNotEmpty(event.getSystemFaction()) && event.getFactions() != null) {
            for (Faction faction : event.getFactions()) {
                if (event.getSystemFaction().equals(faction.getName())) {
                    this.setSystemState(faction.getFactionState());
                    break;
                }
            }
        }
        this.setSystemGovernment(event.getSystemGovernment());
        this.setSystemSecurity(event.getSystemSecurity());

        VisitedStarSystem visitedStarSystem = new VisitedStarSystem();
        visitedStarSystem.setName(event.getStarSystem());
        visitedStarSystem.setCoord(event.getStarPos());
        visitedStarSystem.setTimestamp(event.getTimestamp());
        this.getVisitedStarSystems().addLast(visitedStarSystem);
    }

    private void updateFromScanEvent(ScanEvent event) {
        ScannedBody scannedBody = new ScannedBody();
        scannedBody.setName(event.getBodyName());
        scannedBody.setTimestamp(event.getTimestamp());
        this.getScannedBodies().addLast(scannedBody);
    }

    private void updateFromLoadGameEvent(LoadGameEvent event) {
        // TODO Auto-generated method stub
    }

    private void updateFromLoadoutEvent(LoadoutEvent event) {
        Ship currentShip = new Ship();
        currentShip.setId(event.getShipID());
        currentShip.setType(event.getShip());
        currentShip.setIdent(event.getShipIdent());
        currentShip.setName(event.getShipName());
        this.setCurrentShip(currentShip);
    }

}
