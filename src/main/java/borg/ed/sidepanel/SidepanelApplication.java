package borg.ed.sidepanel;

import javax.swing.UIManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import borg.ed.sidepanel.commander.CommanderData;
import borg.ed.sidepanel.gui.SidePanelFrame;
import borg.ed.universe.UniverseApplication;

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
