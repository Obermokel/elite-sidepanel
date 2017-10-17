package borg.ed.sidepanel.commander;

import borg.ed.universe.data.Coord;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;

/**
 * OtherCommanderLocation
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
@Getter
@Setter
public class OtherCommanderLocation {

    static final Logger logger = LoggerFactory.getLogger(OtherCommanderLocation.class);

    private String commanderName = null;

    private String starSystemName = null;

    private Coord coord = null;

    private ZonedDateTime timestamp = null;

}
