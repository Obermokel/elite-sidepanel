package borg.ed.sidepanel.commander;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * ScannedBody
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
@Getter
@Setter
public class ScannedBody implements Serializable {

    private static final long serialVersionUID = -4334049089980664227L;

    static final Logger logger = LoggerFactory.getLogger(ScannedBody.class);

    private String name = null;

    private ZonedDateTime timestamp = null;

}
