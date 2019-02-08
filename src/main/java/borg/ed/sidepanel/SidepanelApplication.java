package borg.ed.sidepanel;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.UIManager;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import borg.ed.galaxy.GalaxyApplication;
import borg.ed.galaxy.journal.JournalEventReader;
import borg.ed.galaxy.journal.events.AbstractJournalEvent;
import borg.ed.galaxy.journal.events.LoadGameEvent;
import borg.ed.sidepanel.commander.CommanderData;
import borg.ed.sidepanel.commander.OtherCommanderLocation;
import borg.ed.sidepanel.gui.SidePanelFrame;

/**
 * SidepanelApplication
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
@Configuration
@Import(GalaxyApplication.class)
public class SidepanelApplication {

	static final Logger logger = LoggerFactory.getLogger(SidepanelApplication.class);

	private static final ApplicationContext APPCTX = new AnnotationConfigApplicationContext(SidepanelApplication.class);

	public static String MY_COMMANDER_NAME = null;

	public static void main(String[] args) throws IOException {
		try {
			UIManager.setLookAndFeel("com.jtattoo.plaf.noire.NoireLookAndFeel");
		} catch (Exception e) {
			e.printStackTrace();
		}

		File journalDir = new File(System.getProperty("user.home"), "Saved Games\\Frontier Developments\\Elite Dangerous");
		if (!journalDir.exists()) {
			journalDir = new File(System.getProperty("user.home"), "Google Drive\\Elite Dangerous\\Journal");
		}
		MY_COMMANDER_NAME = lookupCurrentCommanderName(journalDir);
		CommanderData commanderData = new CommanderData(MY_COMMANDER_NAME, journalDir);
		Map<String, OtherCommanderLocation> otherCommanders = new TreeMap<>();

		SidePanelFrame frame = new SidePanelFrame("SidePanel", APPCTX, commanderData, otherCommanders);
		frame.setVisible(true);
	}

	private static String lookupCurrentCommanderName(File journalDir) throws IOException {
		File[] journalFiles = journalDir.listFiles(new FileFilter() {
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

				return -1 * new Long(ts1).compareTo(new Long(ts2));
			}
		});

		JournalEventReader reader = new JournalEventReader();
		List<String> lines = FileUtils.readLines(journalFiles[0], "UTF-8");
		for (int lineNo = 1; lineNo <= lines.size(); lineNo++) {
			String line = lines.get(lineNo - 1);
			try {
				AbstractJournalEvent event = reader.readLine(line);

				if (event != null) {
					if (event instanceof LoadGameEvent) {
						return ((LoadGameEvent) event).getCommander();
					}
				}
			} catch (Exception e) {
				logger.error("Failed to process line " + lineNo + " of " + journalFiles[0] + "\n\t" + line, e);
			}
		}

		return null;
	}

}
