package borg.ed.sidepanel.commander;

import borg.ed.universe.data.Coord;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * VisitedStarSystem
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
@Getter
@Setter
public class VisitedStarSystem implements Serializable {

    private static final long serialVersionUID = 3325592135848639759L;

    static final Logger logger = LoggerFactory.getLogger(VisitedStarSystem.class);

    private String name = null;

    private Coord coord = null;

    private ZonedDateTime timestamp = null;

}
