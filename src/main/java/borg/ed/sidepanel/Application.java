package borg.ed.sidepanel;

import borg.ed.sidepanel.commander.CommanderData;
import borg.ed.sidepanel.gui.SidePanelFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.swing.UIManager;

/**
 * Application
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
@Configuration
@Import(borg.ed.universe.Application.class)
public class Application {

    static final Logger logger = LoggerFactory.getLogger(Application.class);

    private static final ApplicationContext APPCTX = new AnnotationConfigApplicationContext(Application.class);

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("com.jtattoo.plaf.noire.NoireLookAndFeel");
        } catch (Exception e) {
            e.printStackTrace();
        }

        CommanderData commanderData = new CommanderData(); // TODO Read entire journal

        SidePanelFrame frame = new SidePanelFrame("SidePanel", APPCTX, commanderData);
        frame.setVisible(true);
    }

}
