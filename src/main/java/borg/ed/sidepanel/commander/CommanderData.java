package borg.ed.sidepanel.commander;

import borg.ed.universe.data.Coord;
import borg.ed.universe.journal.events.AbstractJournalEvent;
import borg.ed.universe.journal.events.FSDJumpEvent;
import borg.ed.universe.journal.events.FSDJumpEvent.Faction;
import borg.ed.universe.journal.events.LoadoutEvent;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CommanderData
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
@Getter
@Setter
public class CommanderData {

    static final Logger logger = LoggerFactory.getLogger(CommanderData.class);

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

    public void updateFromJournalEvent(AbstractJournalEvent event) {
        if (event instanceof FSDJumpEvent) {
            this.updateFromFsdJumpEvent((FSDJumpEvent) event);
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
