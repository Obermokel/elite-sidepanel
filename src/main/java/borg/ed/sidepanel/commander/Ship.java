package borg.ed.sidepanel.commander;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * Ship
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
@Getter
@Setter
public class Ship implements Serializable {

    private static final long serialVersionUID = 4133122198594496790L;

    static final Logger logger = LoggerFactory.getLogger(Ship.class);

    private Long id = null;

    private String type = null;

    private String ident = null;

    private String name = null;

}
