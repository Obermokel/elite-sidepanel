package borg.ed.sidepanel;

import borg.ed.sidepanel.commander.CommanderData;
import borg.ed.sidepanel.commander.OtherCommanderLocation;
import borg.ed.sidepanel.gui.SidePanelFrame;
import borg.ed.universe.UniverseApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.UIManager;

/**
 * SidepanelApplication
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
@Configuration
@Import(UniverseApplication.class)
public class SidepanelApplication {

    static final Logger logger = LoggerFactory.getLogger(SidepanelApplication.class);

    private static final ApplicationContext APPCTX = new AnnotationConfigApplicationContext(SidepanelApplication.class);

    public static final String MY_COMMANDER_NAME = "Mokel DeLorean [GPL]";

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
        CommanderData commanderData = new CommanderData(MY_COMMANDER_NAME, journalDir);
        Map<String, OtherCommanderLocation> otherCommanders = new TreeMap<>();

        SidePanelFrame frame = new SidePanelFrame("SidePanel", APPCTX, commanderData, otherCommanders);
        frame.setVisible(true);
    }

}
