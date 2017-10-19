package borg.ed.sidepanel.gui;

import borg.ed.sidepanel.commander.CommanderData;
import borg.ed.sidepanel.commander.OtherCommanderLocation;
import borg.ed.sidepanel.commander.VisitedStarSystem;
import borg.ed.universe.constants.Element;
import borg.ed.universe.constants.PlanetClass;
import borg.ed.universe.constants.StarClass;
import borg.ed.universe.constants.TerraformingState;
import borg.ed.universe.data.Coord;
import borg.ed.universe.model.Body;
import borg.ed.universe.model.Body.MaterialShare;
import borg.ed.universe.model.StarSystem;
import borg.ed.universe.service.UniverseService;
import borg.ed.universe.util.BodyUtil;
import borg.ed.universe.util.MiscUtil;
import borg.ed.universe.util.StarUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.util.CloseableIterator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * DiscoveryPanel
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
public class DiscoveryPanel extends JPanel {

    private static final long serialVersionUID = 2933866499279397227L;

    static final Logger logger = LoggerFactory.getLogger(DiscoveryPanel.class);

    private final CommanderData commanderData;

    private UniverseService universeService = null;

    private JTextArea txtClosestNeutronStars = new JTextArea(5, 40);
    private JTextArea txtClosestValuableSystems = new JTextArea(10, 40);
    private JTextArea txtClosestJumponiumBodies = new JTextArea(10, 40);
    private JTextArea txtKnownBodies = new JTextArea(3, 40);
    private JTextArea txtValuableBodies = new JTextArea(3, 40);
    private Area area = null;

    public DiscoveryPanel(ApplicationContext appctx, CommanderData commanderData, Map<String, OtherCommanderLocation> otherCommanders) {
        this.commanderData = commanderData;

        this.universeService = appctx.getBean(UniverseService.class);

        this.setLayout(new BorderLayout());

        Box box = new Box(BoxLayout.Y_AXIS);
        Font font = new Font("Sans Serif", Font.BOLD, 18);
        this.txtClosestNeutronStars.setFont(font);
        this.txtClosestValuableSystems.setFont(font);
        this.txtClosestJumponiumBodies.setFont(font);
        this.txtKnownBodies.setFont(font);
        this.txtKnownBodies.setLineWrap(true);
        this.txtValuableBodies.setFont(font);
        this.txtValuableBodies.setLineWrap(true);

        box.add(new JLabel("Closest neutron stars:"));
        box.add(this.txtClosestNeutronStars);
        box.add(new JLabel(" "));
        box.add(new JLabel("Closest valuable systems:"));
        box.add(this.txtClosestValuableSystems);
        box.add(new JLabel(" "));
        box.add(new JLabel("Closest jumponium rich bodies:"));
        box.add(this.txtClosestJumponiumBodies);
        box.add(new JLabel(" "));
        box.add(new JLabel("Known bodies in system:"));
        box.add(this.txtKnownBodies);
        box.add(new JLabel(" "));
        box.add(new JLabel("Valuable bodies in system:"));
        box.add(this.txtValuableBodies);
        box.add(new JLabel(" "));
        JPanel dummyPanel = new JPanel(new BorderLayout());
        dummyPanel.add(box, BorderLayout.NORTH);
        dummyPanel.add(new JLabel(""), BorderLayout.CENTER);
        this.add(dummyPanel, BorderLayout.WEST);

        this.area = new Area(appctx, commanderData, otherCommanders);
        this.add(this.area, BorderLayout.CENTER);
    }

    public void updateFromElasticsearch(boolean knownOnly) {
        final Coord coord = this.commanderData.getCurrentCoord();
        if (coord == null) {
            return;
        }

        List<Body> knownBodies = this.universeService.findBodiesByStarSystemName(this.commanderData.getCurrentStarSystem());
        this.txtKnownBodies.setText(knownBodies.stream() //
                .filter(b -> !b.getName().toLowerCase().contains("belt")) //
                .sorted((b1, b2) -> b1.getName().toLowerCase().compareTo(b2.getName().toLowerCase())) //
                .map(b -> b.getName().replace(b.getStarSystemName(), "").trim()) //
                .map(name -> StringUtils.isEmpty(name) ? "MAIN" : name) //
                .collect(Collectors.joining(", ")));
        this.txtValuableBodies.setText(knownBodies.stream() //
                .filter(b -> BodyUtil.estimatePayout(b) >= 10_000) //
                .sorted((b1, b2) -> -1 * new Long(BodyUtil.estimatePayout(b1)).compareTo(BodyUtil.estimatePayout(b2))) //
                .map(b -> String.format(Locale.US, "%s: %,d CR", b.getName().replace(b.getStarSystemName(), "").trim(), BodyUtil.estimatePayout(b))) //
                .collect(Collectors.joining(", ")));

        if (!knownOnly) {
            StringBuilder neutronStarsText = new StringBuilder();
            List<Body> neutronStars = this.findNearbyNeutronStars(coord, /* range = */ 250f);
            for (int i = 0; i < Math.min(5, neutronStars.size()); i++) {
                Body body = neutronStars.get(i);
                neutronStarsText.append(String.format(Locale.US, "%.0f Ly -- %s\n", body.getCoord().distanceTo(coord), body.getName()));
            }
            this.txtClosestNeutronStars.setText(neutronStarsText.toString().trim());

            StringBuilder valuableSystemsText = new StringBuilder();
            LinkedHashMap<String, Long> valuableSystems = this.findNearbyValuableSystems(coord, /* range = */ 250f);
            int counter = 0;
            for (String systemName : valuableSystems.keySet()) {
                if (counter++ < 10) {
                    long payout = valuableSystems.get(systemName);
                    valuableSystemsText.append(String.format(Locale.US, "%,d CR -- %s\n", payout, systemName));
                }
            }
            this.txtClosestValuableSystems.setText(valuableSystemsText.toString().trim());

            StringBuilder jumponiumBodiesText = new StringBuilder();
            List<Body> jumponiumRichBodies = this.findNearbyJumponiumRichBodies(coord, /* range = */ 250f);
            for (int i = 0; i < Math.min(10, jumponiumRichBodies.size()); i++) {
                Body body = jumponiumRichBodies.get(i);
                String mats = body.getMaterialShares().stream() //
                        .filter(sh -> sh.getPercent() != null && sh.getPercent().floatValue() > 0) //
                        .filter(sh -> Element.POLONIUM.equals(sh.getName()) || Element.YTTRIUM.equals(sh.getName()) || Element.NIOBIUM.equals(sh.getName()) || Element.ARSENIC.equals(sh.getName())) //
                        .sorted((sh1, sh2) -> sh1.getName().name().compareTo(sh2.getName().name())) //
                        .map(sh -> String.format(Locale.US, "%.1f%% %s", sh.getPercent().floatValue(), sh.getName().name().substring(0, 3))) //
                        .collect(Collectors.joining(", "));
                jumponiumBodiesText.append(String.format(Locale.US, "%.0f Ly -- %s -- %s\n", body.getCoord().distanceTo(coord), body.getName(), mats));
            }
            this.txtClosestJumponiumBodies.setText(jumponiumBodiesText.toString().trim());

            this.area.updateFromElasticsearch();
        }
    }

    private List<Body> findNearbyNeutronStars(final Coord coord, final float range) {
        List<Body> result = new ArrayList<>();

        try {
            try (CloseableIterator<Body> stream = this.universeService.streamStarsNear(coord, range, /* isMainStar = */ Boolean.TRUE, Arrays.asList(StarClass.N))) {
                stream.forEachRemaining(body -> {
                    if (body.getStarClass() != null) {
                        result.add(body);
                    }
                });
            }

            // Sort by distance
            if (!result.isEmpty()) {
                Collections.sort(result, new Comparator<Body>() {
                    @Override
                    public int compare(Body b1, Body b2) {
                        return new Float(b1.getCoord().distanceTo(coord)).compareTo(new Float(b2.getCoord().distanceTo(coord)));
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Failed to find nearby neutron stars", e);
        }

        return result;
    }

    private List<Body> findNearbyJumponiumRichBodies(final Coord coord, final float range) {
        List<Body> result = new ArrayList<>();

        try {
            MaterialShare pol = new MaterialShare();
            pol.setName(Element.POLONIUM);
            pol.setPercent(new BigDecimal("0.5"));
            MaterialShare ytt = new MaterialShare();
            ytt.setName(Element.YTTRIUM);
            ytt.setPercent(new BigDecimal("1.0"));
            MaterialShare nio = new MaterialShare();
            nio.setName(Element.NIOBIUM);
            nio.setPercent(new BigDecimal("1.5"));
            MaterialShare ars = new MaterialShare();
            ars.setName(Element.ARSENIC);
            ars.setPercent(new BigDecimal("2.0"));

            // Polonium
            Page<Body> page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(pol, nio, ars), PageRequest.of(0, 100));
            while (page != null) {
                for (Body body : page.getContent()) {
                    if (!result.contains(body)) {
                        result.add(body);
                    }
                }
                if (page.hasNext()) {
                    page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(pol, nio, ars), page.nextPageable());
                } else {
                    page = null;
                }
            }

            // Yttrium
            page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(ytt, nio, ars), PageRequest.of(0, 100));
            while (page != null) {
                for (Body body : page.getContent()) {
                    if (!result.contains(body)) {
                        result.add(body);
                    }
                }
                if (page.hasNext()) {
                    page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(ytt, nio, ars), page.nextPageable());
                } else {
                    page = null;
                }
            }

            // Sort by distance
            if (!result.isEmpty()) {
                Collections.sort(result, new Comparator<Body>() {
                    @Override
                    public int compare(Body b1, Body b2) {
                        return new Float(b1.getCoord().distanceTo(coord)).compareTo(new Float(b2.getCoord().distanceTo(coord)));
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Failed to find nearby jumponium rich bodies", e);
        }

        return result;
    }

    /**
     * @return
     *      Map&lt;systemName, systemPayout&gt;
     */
    private LinkedHashMap<String, Long> findNearbyValuableSystems(final Coord coord, final float range) {
        LinkedHashMap<String, Long> valueBySystem = new LinkedHashMap<>();

        try {
            final long maxDistanceFromArrival = 10_000L; // Ls
            final long minValue = 1_000_000L; // CR

            Set<String> starSystemNames = new HashSet<>();

            List<PlanetClass> elwWwAw = Arrays.asList(PlanetClass.EARTHLIKE_BODY, PlanetClass.WATER_WORLD, PlanetClass.AMMONIA_WORLD);
            Page<Body> page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ null, elwWwAw, PageRequest.of(0, 100));
            while (page != null) {
                starSystemNames.addAll(page.getContent().stream().map(Body::getStarSystemName).filter(name -> StringUtils.isNotEmpty(name)).collect(Collectors.toList()));
                if (page.hasNext()) {
                    page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ null, elwWwAw, page.nextPageable());
                } else {
                    page = null;
                }
            }

            page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ Boolean.TRUE, null, PageRequest.of(0, 100));
            while (page != null) {
                starSystemNames.addAll(page.getContent().stream().map(Body::getStarSystemName).filter(name -> StringUtils.isNotEmpty(name)).collect(Collectors.toList()));
                if (page.hasNext()) {
                    page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ Boolean.TRUE, null, page.nextPageable());
                } else {
                    page = null;
                }
            }

            for (String starSystemName : starSystemNames) {
                long systemPayout = 0L;

                List<Body> bodies = this.universeService.findBodiesByStarSystemName(starSystemName);
                for (Body body : bodies) {
                    if (body.getDistanceToArrival() != null && body.getDistanceToArrival().longValue() <= maxDistanceFromArrival) {
                        systemPayout += BodyUtil.estimatePayout(body.getStarClass(), body.getPlanetClass(), TerraformingState.TERRAFORMABLE.equals(body.getTerraformingState()));
                    }
                }

                if (systemPayout >= minValue) {
                    valueBySystem.put(starSystemName, systemPayout);
                }
            }

            MiscUtil.sortMapByValueReverse(valueBySystem);
        } catch (Exception e) {
            logger.error("Failed to find nearby valuable systems", e);
        }

        return valueBySystem;
    }

    public static class Area extends JPanel {

        private static final long serialVersionUID = 8383226308842901529L;

        private final CommanderData commanderData;
        private final Map<String, OtherCommanderLocation> otherCommanders;

        private UniverseService universeService = null;

        private float xsize = 25f;
        private float xfrom = 0f - xsize;
        private float xto = 0f + xsize;
        private float ysize = 25f;
        private float yfrom = 0f - ysize;
        private float yto = 0f + ysize;
        private float zsize = 25f;
        private float zfrom = 0f - zsize;
        private float zto = 0f + zsize;

        public Area(ApplicationContext appctx, CommanderData commanderData, Map<String, OtherCommanderLocation> otherCommanders) {
            this.commanderData = commanderData;
            this.otherCommanders = otherCommanders;

            this.universeService = appctx.getBean(UniverseService.class);
        }

        public void updateFromElasticsearch() {
            this.repaint();
        }

        @Override
        public void paint(Graphics g) {
            super.paintComponent(g);

            // Black background
            g.setColor(new Color(20, 20, 25));
            g.fillRect(0, 0, this.getWidth(), this.getHeight());

            Coord coord = this.commanderData.getCurrentCoord();
            if (coord == null) {
                return;
            }

            zsize = 2 * 500f;
            zfrom = coord.getZ() - zsize / 2;
            zto = coord.getZ() + zsize / 2;
            xsize = ((float) this.getWidth() / (float) this.getHeight()) * zsize;
            xfrom = coord.getX() - xsize / 2;
            xto = coord.getX() + xsize / 2;
            ysize = Math.min(xsize, zsize);
            yfrom = coord.getY() - ysize / 2;
            yto = coord.getY() + ysize / 2;
            int tmp = Math.round(this.getHeight() / 500f);
            final int psize = tmp % 2 == 0 ? tmp + 1 : tmp;
            final int poffset = (psize - 1) / 2;

            // My travel history
            if (this.commanderData.getVisitedStarSystems().size() >= 2) {
                int toIndex = this.commanderData.getVisitedStarSystems().size();
                int fromIndex = Math.max(0, toIndex - 32);
                int alpha = 0;
                Point prev = null;
                for (int idx = fromIndex; idx < toIndex; idx++) {
                    VisitedStarSystem visitedStarSystem = this.commanderData.getVisitedStarSystems().get(idx);
                    Point curr = this.coordToPoint(visitedStarSystem.getCoord());
                    if (prev != null) {
                        alpha += 8;
                        //((Graphics2D) g).setStroke(new BasicStroke(3));
                        g.setColor(new Color(140, 140, 140, alpha));
                        g.drawLine(prev.x, prev.y, curr.x, curr.y);
                    }
                    prev = curr;
                }
            }

            // Known systems
            try (CloseableIterator<StarSystem> stream = this.universeService.streamAllSystemsWithin(xfrom, xto, yfrom, yto, zfrom, zto)) {
                stream.forEachRemaining(system -> {
                    Point p = this.coordToPoint(system.getCoord());
                    float dy = Math.abs(system.getCoord().getY() - coord.getY());
                    int alpha = 255 - Math.round((dy / (ysize / 2)) * 255);

                    g.setColor(new Color(80, 80, 80, alpha));
                    g.fillRect(p.x - poffset, p.y - poffset, psize, psize);
                    //g.fillRect(p.x, p.y, 1, 1);
                });
            }

            // Known entry stars
            try (CloseableIterator<Body> stream = this.universeService.streamStarsWithin(xfrom, xto, yfrom, yto, zfrom, zto, /* isMainStar = */ Boolean.TRUE, /* starClasses = */ null)) {
                stream.forEachRemaining(mainStar -> {
                    if (mainStar.getStarClass() != null) {
                        Point p = this.coordToPoint(mainStar.getCoord());
                        float dy = Math.abs(mainStar.getCoord().getY() - coord.getY());
                        int alpha = 255 - Math.round((dy / (ysize / 2)) * 127);

                        Color color = StarUtil.starClassToColor(mainStar.getStarClass());
                        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                        g.fillRect(p.x - poffset, p.y - poffset, psize, psize);
                        //g.fillRect(p.x, p.y, 1, 1);
                    }
                });
            }

            // Other commanders
            g.setColor(Color.CYAN);
            g.setFont(new Font("Sans Serif", Font.PLAIN, 12));
            for (OtherCommanderLocation location : this.otherCommanders.values()) {
                Point p = this.coordToPoint(location.getCoord());
                //g.fillRect(p.x - poffset, p.y - poffset, psize, psize);
                g.drawString(location.getCommanderName(), p.x, p.y);
            }
        }

        private Point coordToPoint(Coord coord) {
            float xPercent = (coord.getX() - this.xfrom) / this.xsize;
            float yPercent = 1.0f - ((coord.getZ() - this.zfrom) / this.zsize);

            return new Point(Math.round(xPercent * this.getWidth()), Math.round(yPercent * this.getHeight()));
        }

    }

}
