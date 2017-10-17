package borg.ed.sidepanel.gui;

import borg.ed.sidepanel.commander.CommanderData;
import borg.ed.universe.constants.PlanetClass;
import borg.ed.universe.constants.StarClass;
import borg.ed.universe.constants.TerraformingState;
import borg.ed.universe.data.Coord;
import borg.ed.universe.model.Body;
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private JTextArea txtClosestNeutronStars = new JTextArea(10, 40);
    private JTextArea txtClosestValuableSystems = new JTextArea(10, 40);
    private Area area = null;

    public DiscoveryPanel(ApplicationContext appctx, CommanderData commanderData) {
        this.commanderData = commanderData;

        this.universeService = appctx.getBean(UniverseService.class);

        this.setLayout(new BorderLayout());

        Box box = new Box(BoxLayout.Y_AXIS);
        Font font = new Font("Sans Serif", Font.BOLD, 18);
        this.txtClosestNeutronStars.setFont(font);
        this.txtClosestValuableSystems.setFont(font);

        box.add(new JLabel("Closest neutron stars:"));
        box.add(this.txtClosestNeutronStars);
        box.add(new JLabel(" "));
        box.add(new JLabel("Closest valuable systems:"));
        box.add(this.txtClosestValuableSystems);
        box.add(new JLabel(" "));
        JPanel dummyPanel = new JPanel(new BorderLayout());
        dummyPanel.add(box, BorderLayout.NORTH);
        dummyPanel.add(new JLabel(""), BorderLayout.CENTER);
        this.add(dummyPanel, BorderLayout.WEST);

        this.area = new Area(appctx, commanderData);
        this.add(this.area, BorderLayout.CENTER);
    }

    private JPanel distanceLabel(String label, JLabel lblDistance) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Sans Serif", Font.BOLD, 18));
        lblDistance.setFont(new Font("Sans Serif", Font.BOLD, 18));
        panel.add(lbl);
        panel.add(lblDistance);
        return panel;
    }

    public void updateFromElasticsearch() {
        final Coord coord = this.commanderData.getCurrentCoord();
        if (coord == null) {
            return;
        }

        List<Body> closestNeutronStars = new ArrayList<>();
        float range = 200;
        int maxNumber = 10;
        Page<Body> page = this.universeService.findStarsNear(coord, range, /* isMainStar = */ Boolean.TRUE, Arrays.asList(StarClass.N), new PageRequest(0, 1000));
        while (page != null) {
            List<Body> bodies = page.getContent();
            closestNeutronStars.addAll(bodies);
            if (page.hasNext()) {
                page = this.universeService.findStarsNear(coord, range, /* isMainStar = */ Boolean.TRUE, Arrays.asList(StarClass.N), page.nextPageable());
            } else {
                page = null;
            }
        }
        Collections.sort(closestNeutronStars, new Comparator<Body>() {
            @Override
            public int compare(Body b1, Body b2) {
                Float d1 = b1.getCoord().distanceTo(coord);
                Float d2 = b2.getCoord().distanceTo(coord);
                return d1.compareTo(d2);
            }
        });
        closestNeutronStars = closestNeutronStars.size() <= maxNumber ? closestNeutronStars : closestNeutronStars.subList(0, maxNumber);
        this.txtClosestNeutronStars.setText(closestNeutronStars.stream().map(b -> b.getName()).collect(Collectors.joining("\n")));

        LinkedHashMap<String, Long> nearbyValuableSystems = this.findNearbyValuableSystems(coord);
        StringBuilder systemPayouts = new StringBuilder();
        int counter = 0;
        for (String systemName : nearbyValuableSystems.keySet()) {
            if (counter++ < maxNumber) {
                long payout = nearbyValuableSystems.get(systemName);
                systemPayouts.append(String.format(Locale.US, "%-30s %,10d CR\n", systemName, payout));
            }
        }
        this.txtClosestValuableSystems.setText(systemPayouts.toString().trim());
    }

    /**
     * @return
     *      Map&lt;systemName, systemPayout&gt;
     */
    private LinkedHashMap<String, Long> findNearbyValuableSystems(final Coord coord) {
        LinkedHashMap<String, Long> valueBySystem = new LinkedHashMap<>();

        try {
            final float range = 200; // Ly
            final long maxDistanceFromArrival = 10_000L; // Ls
            final long minValue = 1_000_000L; // CR

            Set<String> starSystemNames = new HashSet<>();

            logger.debug("Searching for ELW, WW and AW...");
            List<PlanetClass> elwWwAw = Arrays.asList(PlanetClass.EARTHLIKE_BODY, PlanetClass.WATER_WORLD, PlanetClass.AMMONIA_WORLD);
            Page<Body> page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ null, elwWwAw, new PageRequest(0, 1000));
            while (page != null) {
                List<Body> bodies = page.getContent();
                starSystemNames.addAll(bodies.stream().map(Body::getStarSystemName).filter(name -> StringUtils.isNotEmpty(name)).collect(Collectors.toList()));
                if (page.hasNext()) {
                    page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ null, elwWwAw, page.nextPageable());
                } else {
                    page = null;
                }
            }
            logger.debug("...found " + starSystemNames.size() + " system(s) until now");

            logger.debug("Searching for TCs...");
            page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ Boolean.TRUE, null, new PageRequest(0, 1000));
            while (page != null) {
                List<Body> bodies = page.getContent();
                starSystemNames.addAll(bodies.stream().map(Body::getStarSystemName).filter(name -> StringUtils.isNotEmpty(name)).collect(Collectors.toList()));
                if (page.hasNext()) {
                    page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ Boolean.TRUE, null, page.nextPageable());
                } else {
                    page = null;
                }
            }
            logger.debug("...found " + starSystemNames.size() + " system(s) until now");

            logger.debug("Evaluating systems...");
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
            logger.debug("..." + valueBySystem.size() + " system(s) left");

            MiscUtil.sortMapByValueReverse(valueBySystem);
        } catch (Exception e) {
            logger.error("Failed to find nearby valuable systems", e);
        }

        return valueBySystem;
    }

    public static class Area extends JPanel {

        private static final long serialVersionUID = 8383226308842901529L;

        private final CommanderData commanderData;

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

        public Area(ApplicationContext appctx, CommanderData commanderData) {
            this.commanderData = commanderData;

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

            //zsize = ((float) this.getHeight() / (float) this.getWidth()) * xsize;
            zsize = 2 * 100f;
            zfrom = coord.getZ() - zsize / 2;
            zto = coord.getZ() + zsize / 2;
            //xsize = 2 * 160f;
            xsize = ((float) this.getWidth() / (float) this.getHeight()) * zsize;
            xfrom = coord.getX() - xsize / 2;
            xto = coord.getX() + xsize / 2;
            //ysize = xsize / 4;
            //ysize = zsize / 4;
            ysize = Math.min(xsize, zsize);
            yfrom = coord.getY() - ysize / 2;
            yto = coord.getY() + ysize / 2;
            //            StarSystemRepository systemRepo = this.appctx.getBean(StarSystemRepository.class);
            //            BodyRepository bodyRepo = this.appctx.getBean(BodyRepository.class);
            int psize = Math.round(this.getWidth() / 150f);
            if (psize % 2 == 0) {
                psize++;
            }
            int poffset = (psize - 1) / 2;
            //            this.universeService this.universeService = this.appctx.getBean(EddbService.class);
            //
            Page<StarSystem> systems = this.universeService.findSystemsWithin(xfrom, xto, yfrom, yto, zfrom, zto, new PageRequest(0, 10000));
            logger.debug("Found " + systems.getTotalElements() + " system(s)");
            for (StarSystem system : systems.getContent()) {
                Point p = this.coordToPoint(system.getCoord());
                float dy = Math.abs(system.getCoord().getY() - coord.getY());
                int alpha = 255 - Math.round((dy / (ysize / 2)) * 255);

                g.setColor(new Color(80, 80, 80, alpha));
                g.fillRect(p.x - 1, p.y - 1, 3, 3);
            }

            Page<Body> mainStars = this.universeService.findStarsWithin(xfrom, xto, yfrom, yto, zfrom, zto, /* isMainStar = */ Boolean.TRUE, /* starClasses = */ null, new PageRequest(0, 10000));
            logger.debug("Found " + mainStars.getTotalElements() + " main star(s) with known spectral class");
            for (Body mainStar : mainStars.getContent()) {
                if (mainStar.getStarClass() != null) {
                    Point p = this.coordToPoint(mainStar.getCoord());
                    float dy = Math.abs(mainStar.getCoord().getY() - coord.getY());
                    int alpha = 255 - Math.round((dy / (ysize / 2)) * 127);

                    Color color = StarUtil.starClassToColor(mainStar.getStarClass());
                    g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
                    g.fillRect(p.x - 1, p.y - 1, 3, 3);
                }
            }
            //
            //            for (int i = this.travelHistory.getVisitedSystems().size() - 1; i >= 0 && i >= this.travelHistory.getVisitedSystems().size() - 1000; i--) {
            //                VisitedSystem visitedSystem = this.travelHistory.getVisitedSystems().get(i);
            //
            //                Point p = this.coordToPoint(visitedSystem.getCoord());
            //                float dy = Math.abs(visitedSystem.getCoord().getY() - coord.getY());
            //                if (dy <= (ysize / 2)) {
            //                    int alpha = 255 - Math.round((dy / (ysize / 2)) * 255);
            //
            //                    g.setColor(new Color(80, 80, 80, alpha));
            //                    g.fillRect(p.x - 1, p.y - 1, 3, 3);
            //
            //                    for (ScannedBody scannedBody : visitedSystem.getScannedBodies()) {
            //                        if (scannedBody.getDistanceFromArrivalLS() != null && scannedBody.getDistanceFromArrivalLS().floatValue() == 0f) {
            //                            alpha = 255 - Math.round((dy / (ysize / 2)) * 127);
            //
            //                            Color color = StarUtil.spectralClassToColor(scannedBody.getStarClass());
            //                            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            //                            g.fillRect(p.x - 1, p.y - 1, 3, 3);
            //                            break;
            //                        }
            //                    }
            //                }
            //            }
            //
            //            // Me
            //            g.setColor(Color.RED); // Unknown on EDDB
            //            try {
            //                StarSystem system = this.universeService.findSystemByName(this.travelHistory.getSystemName());
            //                if (system != null) {
            //                    g.setColor(Color.ORANGE); // Coords known on EDDB
            //                    List<Body> bodies = this.universeService.findBodiesOfSystem(system.getId());
            //                    for (Body body : bodies) {
            //                        if (Boolean.TRUE.equals(body.getIsMainStar())) {
            //                            if (StringUtils.isNotEmpty(body.getStarClass())) {
            //                                g.setColor(Color.GREEN); // Main star spectral class known on EDDB
            //                            }
            //                            break;
            //                        }
            //                    }
            //                }
            //            } catch (BeansException e) {
            //                logger.error("Failed to find current system '" + this.travelHistory.getSystemName() + "'", e);
            //            }
            //            if (!g.getColor().equals(Color.GREEN)) {
            //                for (int i = this.travelHistory.getVisitedSystems().size() - 1; !g.getColor().equals(Color.GREEN) && i >= 0 && i >= this.travelHistory.getVisitedSystems().size() - 1000; i--) {
            //                    VisitedSystem visitedSystem = this.travelHistory.getVisitedSystems().get(i);
            //                    if (visitedSystem.getCoord().distanceTo(coord) <= 0.01f) {
            //                        for (ScannedBody scannedBody : visitedSystem.getScannedBodies()) {
            //                            if (scannedBody.getDistanceFromArrivalLS() != null && scannedBody.getDistanceFromArrivalLS().floatValue() == 0f) {
            //                                g.setColor(Color.GREEN);
            //                                break;
            //                            }
            //                        }
            //                        break;
            //                    }
            //                }
            //            }
            //            Point p = this.coordToPoint(coord);
            //            g.fillRect(p.x - poffset, p.y - poffset, psize, psize);
            //
            //            g.setColor(Color.CYAN);
            //            g.setFont(new Font("Sans Serif", Font.PLAIN, 12));
            //            for (String commanderName : this.commanderLocations.keySet()) {
            //                p = this.coordToPoint(this.commanderLocations.get(commanderName));
            //                g.fillRect(p.x - poffset, p.y - poffset, psize, psize);
            //                g.drawString(commanderName, p.x, p.y);
            //            }
        }

        private Point coordToPoint(Coord coord) {
            float xPercent = (coord.getX() - this.xfrom) / this.xsize;
            float yPercent = 1.0f - ((coord.getZ() - this.zfrom) / this.zsize);

            return new Point(Math.round(xPercent * this.getWidth()), Math.round(yPercent * this.getHeight()));
        }

    }

}
