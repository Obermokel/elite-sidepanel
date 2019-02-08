package borg.ed.sidepanel.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.util.CloseableIterator;

import borg.ed.galaxy.constants.Element;
import borg.ed.galaxy.constants.PlanetClass;
import borg.ed.galaxy.constants.StarClass;
import borg.ed.galaxy.constants.TerraformingState;
import borg.ed.galaxy.data.Coord;
import borg.ed.galaxy.exceptions.NonUniqueResultException;
import borg.ed.galaxy.model.Body;
import borg.ed.galaxy.model.Body.MaterialShare;
import borg.ed.galaxy.model.StarSystem;
import borg.ed.galaxy.service.GalaxyService;
import borg.ed.galaxy.util.BodyUtil;
import borg.ed.galaxy.util.MiscUtil;
import borg.ed.galaxy.util.StarUtil;
import borg.ed.sidepanel.commander.CommanderData;
import borg.ed.sidepanel.commander.OtherCommanderLocation;
import borg.ed.sidepanel.commander.VisitedStarSystem;

/**
 * DiscoveryPanel
 *
 * @author <a href="mailto:b.guenther@xsite.de">Boris Guenther</a>
 */
public class DiscoveryPanel extends JPanel {

	private static final long serialVersionUID = 2933866499279397227L;

	static final Logger logger = LoggerFactory.getLogger(DiscoveryPanel.class);

	private final CommanderData commanderData;

	private GalaxyService galaxyService = null;
	private Set<String> populatedSystems = new HashSet<>();
	private Map<String, Long> knownPayouts = new HashMap<>();

	private JTextArea txtClosestNeutronStars = new JTextArea(5, 40);
	private JTextArea txtClosestValuableSystems = new JTextArea(10, 40);
	private JTextArea txtClosestJumponiumBodies = new JTextArea(10, 40);
	private JTextArea txtKnownBodies = new JTextArea(3, 40);
	private JTextArea txtValuableBodies = new JTextArea(3, 40);
	private Area area = null;

	public DiscoveryPanel(ApplicationContext appctx, CommanderData commanderData, Map<String, OtherCommanderLocation> otherCommanders) {
		this.commanderData = commanderData;

		this.galaxyService = appctx.getBean(GalaxyService.class);

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

	public void updateFromElasticsearch(boolean repaintMap) {
		final Coord coord = this.commanderData.getCurrentCoord();
		if (coord == null) {
			return;
		}

		logger.trace("Searching for known bodies in " + this.commanderData.getCurrentStarSystem());
		List<Body> knownBodies = this.galaxyService.findBodiesByStarSystemName(this.commanderData.getCurrentStarSystem());
		this.txtKnownBodies.setText(knownBodies.stream() //
				.filter(b -> !b.getName().toLowerCase().contains("belt")) //
				.sorted((b1, b2) -> b1.getName().toLowerCase().compareTo(b2.getName().toLowerCase())) //
				.map(b -> b.getName().replace(b.getStarSystemName(), "").trim()) //
				.map(name -> StringUtils.isEmpty(name) ? "MAIN" : name) //
				.collect(Collectors.joining(", ")));

		logger.trace("Searching for valuable bodies in " + this.commanderData.getCurrentStarSystem());
		this.txtValuableBodies.setText(knownBodies.stream() //
				.filter(b -> BodyUtil.estimatePayout(b) >= 50_000) //
				.filter(b -> !this.commanderData.scannedBody(b.getName())) //
				.sorted((b1, b2) -> -1 * new Long(BodyUtil.estimatePayout(b1)).compareTo(BodyUtil.estimatePayout(b2))) //
				.map(b -> String.format(Locale.US, "%s: %,d CR", b.getName().replace(b.getStarSystemName(), "").trim(), BodyUtil.estimatePayout(b))) //
				.collect(Collectors.joining(", ")));

		StringBuilder neutronStarsText = new StringBuilder();
		List<Body> neutronStars = this.findNearbyNeutronStars(coord, /* range = */ 250f);
		for (int i = 0; i < Math.min(5, neutronStars.size()); i++) {
			Body body = neutronStars.get(i);
			neutronStarsText.append(String.format(Locale.US, "%.0f Ly -- %s\n", body.getCoord().distanceTo(coord), body.getName()));
		}
		this.txtClosestNeutronStars.setText(neutronStarsText.toString().trim());

		StringBuilder valuableSystemsText = new StringBuilder();
		LinkedHashMap<String, Long> valuableSystems = this.findNearbyValuableSystems(coord, /* range = */ Math.min(500f, this.getVisibleDistance()), this.commanderData);
		int counter = 0;
		for (String systemName : valuableSystems.keySet()) {
			float distance = 0f;
			try {
				StarSystem starSystem = this.galaxyService.findStarSystemByName(systemName);
				distance = starSystem == null || starSystem.getCoord() == null ? 0f : starSystem.getCoord().distanceTo(coord);
			} catch (NonUniqueResultException e) {
				// Ignore
			}
			if (counter++ < 10) {
				long payout = valuableSystems.get(systemName);
				valuableSystemsText.append(String.format(Locale.US, "%.0f Ly -- %s -- %,d CR\n", distance, systemName, payout));
			}
		}
		this.txtClosestValuableSystems.setText(valuableSystemsText.toString().trim());

		StringBuilder jumponiumBodiesText = new StringBuilder();
		int nJumponium = 0;
		List<Body> polPlusFiveBodies = this.findNearbyJumponiumPlusFiveBodies(coord, /* range = */ 1000f, Element.POLONIUM);
		for (int i = 0; i < Math.min(10 - nJumponium, polPlusFiveBodies.size()); i++) {
			Body body = polPlusFiveBodies.get(i);
			jumponiumBodiesText.append(String.format(Locale.US, "%.0f Ly -- %s -- Pol+5\n", body.getCoord().distanceTo(coord), body.getName()));
		}
		nJumponium += polPlusFiveBodies.size();
		List<Body> yttPlusFiveBodies = this.findNearbyJumponiumPlusFiveBodies(coord, /* range = */ 1000f, Element.YTTRIUM);
		for (int i = 0; i < Math.min(10 - nJumponium, yttPlusFiveBodies.size()); i++) {
			Body body = yttPlusFiveBodies.get(i);
			jumponiumBodiesText.append(String.format(Locale.US, "%.0f Ly -- %s -- Ytt+5\n", body.getCoord().distanceTo(coord), body.getName()));
		}
		nJumponium += yttPlusFiveBodies.size();
		List<Body> jumponiumRichBodies = this.findNearbyJumponiumRichBodies(coord, /* range = */ 250f);
		for (int i = 0; i < Math.min(10 - nJumponium, jumponiumRichBodies.size()); i++) {
			Body body = jumponiumRichBodies.get(i);
			String mats = body.getMaterialShares().stream() //
					.filter(sh -> sh.getPercent() != null && sh.getPercent().floatValue() > 0) //
					.filter(sh -> Element.POLONIUM.equals(sh.getName()) || Element.YTTRIUM.equals(sh.getName()) || Element.NIOBIUM.equals(sh.getName()) || Element.ARSENIC.equals(sh.getName())) //
					.sorted((sh1, sh2) -> sh1.getName().name().compareTo(sh2.getName().name())) //
					.map(sh -> String.format(Locale.US, "%.1f%% %s", sh.getPercent().floatValue(), sh.getName().name().substring(0, 3))) //
					.collect(Collectors.joining(", "));
			jumponiumBodiesText.append(String.format(Locale.US, "%.0f Ly -- %s -- %s\n", body.getCoord().distanceTo(coord), body.getName(), mats));
		}
		nJumponium += jumponiumRichBodies.size();
		List<StarSystem> jumponiumRichSystems = this.findNearbyJumponiumRichSystems(coord, /* range = */ 250f);
		for (int i = 0; i < Math.min(10 - nJumponium, jumponiumRichSystems.size()); i++) {
			StarSystem starSystem = jumponiumRichSystems.get(i);

			Map<Element, BigDecimal> totalMaterials = this.sumMaterialsOfSystem(starSystem.getName());
			BigDecimal polonium = totalMaterials.getOrDefault(Element.POLONIUM, BigDecimal.ZERO);
			BigDecimal yttrium = totalMaterials.getOrDefault(Element.YTTRIUM, BigDecimal.ZERO);
			BigDecimal niobium = totalMaterials.getOrDefault(Element.NIOBIUM, BigDecimal.ZERO);
			String mats = String.format(Locale.US, "%.1f%% Po, %.1f%% Y, %.1f%% Nb, +4", polonium, yttrium, niobium);
			jumponiumBodiesText.append(String.format(Locale.US, "%.0f Ly -- %s -- %s\n", starSystem.getCoord().distanceTo(coord), starSystem.getName(), mats));
		}
		nJumponium += jumponiumRichSystems.size();
		this.txtClosestJumponiumBodies.setText(jumponiumBodiesText.toString().trim());

		if (repaintMap) {
			this.area.updateFromElasticsearch();
		}
	}

	private List<Body> findNearbyNeutronStars(final Coord coord, final float range) {
		List<Body> result = new ArrayList<>();

		try {
			logger.trace("Searching for neutron stars " + range + " Ly around " + coord);

			try (CloseableIterator<Body> stream = this.galaxyService.streamStarsNear(coord, range, /* isMainStar = */ Boolean.TRUE, Arrays.asList(StarClass.N))) {
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

	private List<Body> findNearbyJumponiumPlusFiveBodies(final Coord coord, final float range, final Element grade5Element) {
		List<Body> result = new ArrayList<>();

		try {
			logger.trace("Searching for jumponium+5 " + range + " Ly around " + coord);

			MaterialShare g5 = new MaterialShare();
			g5.setName(grade5Element);
			MaterialShare nio = new MaterialShare();
			nio.setName(Element.NIOBIUM);
			MaterialShare ars = new MaterialShare();
			ars.setName(Element.ARSENIC);
			MaterialShare cad = new MaterialShare();
			cad.setName(Element.CADMIUM);
			MaterialShare ger = new MaterialShare();
			ger.setName(Element.GERMANIUM);
			MaterialShare van = new MaterialShare();
			van.setName(Element.VANADIUM);

			Page<Body> page = this.galaxyService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(g5, nio, ars, cad, ger, van), PageRequest.of(0, 10000));
			while (page != null) {
				for (Body body : page.getContent()) {
					result.add(body);
				}
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.galaxyService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(g5, nio, ars, cad, ger, van), page.nextPageable());
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
			logger.error("Failed to find nearby Jump+5 bodies", e);
		}

		return result;
	}

	private List<Body> findNearbyJumponiumRichBodies(final Coord coord, final float range) {
		List<Body> result = new ArrayList<>();

		try {
			logger.trace("Searching for jumponium rich bodies " + range + " Ly around " + coord);

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
			Page<Body> page = this.galaxyService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(pol, nio, ars), PageRequest.of(0, 10000));
			while (page != null) {
				for (Body body : page.getContent()) {
					if (!result.contains(body)) {
						result.add(body);
					}
				}
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.galaxyService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(pol, nio, ars), page.nextPageable());
				} else {
					page = null;
				}
			}

			// Yttrium
			page = this.galaxyService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(ytt, nio, ars), PageRequest.of(0, 10000));
			while (page != null) {
				for (Body body : page.getContent()) {
					if (!result.contains(body)) {
						result.add(body);
					}
				}
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.galaxyService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(ytt, nio, ars), page.nextPageable());
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
	private LinkedHashMap<String, Long> findNearbyValuableSystems(final Coord coord, final float range, CommanderData commanderData) {
		LinkedHashMap<String, Long> valueBySystem = new LinkedHashMap<>();

		try {
			logger.trace("Searching for valuable systems " + range + " Ly around " + coord);

			final long maxDistanceFromArrival = 10_000L; // Ls
			final long minValue = 1_000_000L; // CR

			Set<String> starSystemNames = new HashSet<>();

			List<PlanetClass> elwWwAw = Arrays.asList(PlanetClass.EARTHLIKE_BODY, PlanetClass.WATER_WORLD, PlanetClass.AMMONIA_WORLD);
			Page<Body> page = this.galaxyService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ null, elwWwAw, PageRequest.of(0, 10000));
			while (page != null) {
				starSystemNames.addAll(page.getContent().stream().map(Body::getStarSystemName).filter(name -> StringUtils.isNotEmpty(name)).collect(Collectors.toList()));
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.galaxyService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ null, elwWwAw, page.nextPageable());
				} else {
					page = null;
				}
			}

			page = this.galaxyService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ Boolean.TRUE, null, PageRequest.of(0, 10000));
			while (page != null) {
				starSystemNames.addAll(page.getContent().stream().map(Body::getStarSystemName).filter(name -> StringUtils.isNotEmpty(name)).collect(Collectors.toList()));
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.galaxyService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ Boolean.TRUE, null, page.nextPageable());
				} else {
					page = null;
				}
			}

			for (String starSystemName : starSystemNames) {
				if (this.knownPayouts.containsKey(starSystemName)) {
					long systemPayout = this.knownPayouts.get(starSystemName);
					if (systemPayout >= minValue && !commanderData.visitedStarSystem(starSystemName)) {
						valueBySystem.put(starSystemName, systemPayout);
					}
				} else {
					if (this.populatedSystems.contains(starSystemName)) {
						continue; // Public knowledge
					}

					boolean visited = commanderData.getVisitedStarSystems().stream().anyMatch(vss -> vss.getName().equals(starSystemName));
					if (visited) {
						continue; // Assume already scanned
					}

					StarSystem starSystem = this.galaxyService.findStarSystemByName(starSystemName);
					if (starSystem != null && starSystem.getPopulation() != null && starSystem.getPopulation().longValue() > 0) {
						this.populatedSystems.add(starSystemName);
						continue; // Public knowledge
					}

					long systemPayout = 0L;

					List<Body> bodies = this.galaxyService.findBodiesByStarSystemName(starSystemName);
					for (Body body : bodies) {
						if (body.getDistanceToArrivalLs() != null && body.getDistanceToArrivalLs().longValue() <= maxDistanceFromArrival) {
							systemPayout += BodyUtil.estimatePayout(body.getStarClass(), body.getPlanetClass(), TerraformingState.TERRAFORMABLE.equals(body.getTerraformingState()));
						}
					}

					this.knownPayouts.put(starSystemName, systemPayout);

					logger.trace(starSystemName + " = " + systemPayout + " CR");

					if (systemPayout >= minValue) {
						valueBySystem.put(starSystemName, systemPayout);
					}
				}
			}

			MiscUtil.sortMapByValueReverse(valueBySystem);
		} catch (Exception e) {
			logger.error("Failed to find nearby valuable systems", e);
		}

		return valueBySystem;
	}

	private List<StarSystem> findNearbyJumponiumRichSystems(final Coord coord, final float range) {
		List<StarSystem> result = new ArrayList<>();

		try {
			logger.trace("Searching for jumponium rich systems " + range + " Ly around " + coord);

			MaterialShare pol = new MaterialShare();
			pol.setName(Element.POLONIUM);
			MaterialShare ytt = new MaterialShare();
			ytt.setName(Element.YTTRIUM);

			Set<String> poloniumSystemNames = new HashSet<>();
			Set<String> yttriumSystemNames = new HashSet<>();

			// Polonium
			Page<Body> page = this.galaxyService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(pol), PageRequest.of(0, 10000));
			while (page != null) {
				for (Body body : page.getContent()) {
					if (StringUtils.isNotEmpty(body.getStarSystemName())) {
						poloniumSystemNames.add(body.getStarSystemName());
					}
				}
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.galaxyService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(pol), page.nextPageable());
				} else {
					page = null;
				}
			}

			// Yttrium
			page = this.galaxyService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(ytt), PageRequest.of(0, 10000));
			while (page != null) {
				for (Body body : page.getContent()) {
					if (StringUtils.isNotEmpty(body.getStarSystemName())) {
						yttriumSystemNames.add(body.getStarSystemName());
					}
				}
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.galaxyService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(ytt), page.nextPageable());
				} else {
					page = null;
				}
			}

			// Intersection
			Set<String> systemNames = new HashSet<>(poloniumSystemNames);
			systemNames.retainAll(yttriumSystemNames);

			for (String systemName : systemNames) {
				StarSystem starSystem = this.galaxyService.findStarSystemByName(systemName);
				if (starSystem != null) {
					Map<Element, BigDecimal> totalMaterials = this.sumMaterialsOfSystem(systemName);
					BigDecimal polonium = totalMaterials.getOrDefault(Element.POLONIUM, BigDecimal.ZERO);
					BigDecimal yttrium = totalMaterials.getOrDefault(Element.YTTRIUM, BigDecimal.ZERO);
					BigDecimal cadmium = totalMaterials.getOrDefault(Element.CADMIUM, BigDecimal.ZERO);
					BigDecimal niobium = totalMaterials.getOrDefault(Element.NIOBIUM, BigDecimal.ZERO);
					BigDecimal arsenic = totalMaterials.getOrDefault(Element.ARSENIC, BigDecimal.ZERO);
					BigDecimal germanium = totalMaterials.getOrDefault(Element.GERMANIUM, BigDecimal.ZERO);
					BigDecimal vanadium = totalMaterials.getOrDefault(Element.VANADIUM, BigDecimal.ZERO);

					if (polonium.floatValue() >= 1.5 && yttrium.floatValue() >= 2 && cadmium.floatValue() > 0 && niobium.floatValue() > 0 && arsenic.floatValue() > 0
							&& germanium.floatValue() > 0 && vanadium.floatValue() > 0) {
						result.add(starSystem);
					}
				}
			}

			// Sort by distance
			if (!result.isEmpty()) {
				Collections.sort(result, new Comparator<StarSystem>() {
					@Override
					public int compare(StarSystem b1, StarSystem b2) {
						return new Float(b1.getCoord().distanceTo(coord)).compareTo(new Float(b2.getCoord().distanceTo(coord)));
					}
				});
			}
		} catch (Exception e) {
			logger.error("Failed to find nearby jumponium rich systems", e);
		}

		return result;
	}

	private Map<Element, BigDecimal> sumMaterialsOfSystem(String systemName) {
		final Map<Element, BigDecimal> result = new TreeMap<>();

		List<Body> bodies = this.galaxyService.findBodiesByStarSystemName(systemName);
		for (Body body : bodies) {
			if (body.getMaterialShares() != null) {
				body.getMaterialShares().stream().filter(sh -> sh.getPercent() != null).forEach(sh -> {
					BigDecimal total = result.getOrDefault(sh.getName(), BigDecimal.ZERO);
					result.put(sh.getName(), total.add(sh.getPercent()));
				});
			}
		}

		return result;
	}

	public static class POI implements Serializable {

		private static final long serialVersionUID = 3186440335442864776L;

		private final String systemName;
		private final String name;
		private final Coord coord;

		public POI(String systemName, GalaxyService galaxyService) throws NonUniqueResultException {
			this(systemName, null, galaxyService);
		}

		public POI(String systemName, String name, GalaxyService galaxyService) throws NonUniqueResultException {
			StarSystem starSystem = galaxyService.findStarSystemByName(systemName);

			this.systemName = starSystem.getName();
			this.name = StringUtils.isEmpty(name) ? starSystem.getName() : name;
			this.coord = starSystem.getCoord();
		}

		public String getSystemName() {
			return systemName;
		}

		public String getName() {
			return name;
		}

		public Coord getCoord() {
			return coord;
		}

	}

	public static class Area extends JPanel implements MouseWheelListener {

		private static final long serialVersionUID = 8383226308842901529L;

		private final CommanderData commanderData;
		private final Map<String, OtherCommanderLocation> otherCommanders;

		private final List<POI> POIS = new ArrayList<>();

		private GalaxyService galaxyService = null;

		float zoom = 100f;
		float xsize = 0f;
		float xfrom = 0f;
		float xto = 0f;
		float ysize = 0f;
		float yfrom = 0f;
		float yto = 0f;
		float zsize = 0f;
		float zfrom = 0f;
		float zto = 0f;

		public Area(ApplicationContext appctx, CommanderData commanderData, Map<String, OtherCommanderLocation> otherCommanders) {
			this.commanderData = commanderData;
			this.otherCommanders = otherCommanders;

			this.galaxyService = appctx.getBean(GalaxyService.class);

			try {
				this.POIS.add(new POI("Sol", galaxyService));
				this.POIS.add(new POI("Colonia", galaxyService));
				this.POIS.add(new POI("Sagittarius A*", galaxyService));
				this.POIS.add(new POI("Maia", galaxyService));
				this.POIS.add(new POI("Betelgeuse", galaxyService));
				this.POIS.add(new POI("VY Canis Majoris", galaxyService));
				this.POIS.add(new POI("Crab Pulsar", galaxyService));

				this.POIS.add(new POI("Maridal", galaxyService));

				this.POIS.add(new POI("Spliehm HW-Y b55-0", "BC#2", galaxyService));
				this.POIS.add(new POI("Phooe Euq XQ-G c10-0", "BC#3", galaxyService));
				this.POIS.add(new POI("Phoi Eur QM-W d1-4", "BC#4", galaxyService));
				//this.POIS.add(new POI("Aicods KD-K d8-3", "BC#5", galaxyService));
				this.POIS.add(new POI("Tradgoe ZU-X e1-0", "BC#6", galaxyService));
				this.POIS.add(new POI("Pra Eorg HC-B d1-0", "BC#7", galaxyService));
				this.POIS.add(new POI("Auzorts AJ-B c13-0", "BC#8", galaxyService));
				this.POIS.add(new POI("Eor Chreou KL-V c16-0", "BC#9", galaxyService));
				this.POIS.add(new POI("Sphieso UE-R d4-5", "BC#10", galaxyService));
				this.POIS.add(new POI("Oombairps DB-U d4-8", "BC#11", galaxyService));
				this.POIS.add(new POI("Nyaugnaae EF-D c1-0", "BC#12", galaxyService));
				this.POIS.add(new POI("Exahn BZ-S d3-13", "BC#13", galaxyService));

				this.POIS.add(new POI("HIP 23759", "WP#0", galaxyService));
				this.POIS.add(new POI("Crab Sector DL-Y d9", "WP#1", galaxyService));
				this.POIS.add(new POI("3 Geminorum", "WP#2", galaxyService));
				this.POIS.add(new POI("Angosk DL-P d5-0", "WP#3", galaxyService));
				this.POIS.add(new POI("Angosk OM-W d1-0", "WP#4", galaxyService));
				this.POIS.add(new POI("Lyed YJ-I d9-0", "WP#5", galaxyService));
				this.POIS.add(new POI("Hypuae Euq ZK-P d5-0", "WP#6", galaxyService));
				this.POIS.add(new POI("Aicods KD-K d8-3", "WP#7", galaxyService));
				this.POIS.add(new POI("Syroifoe CL-Y g1", "WP#8", galaxyService));
				this.POIS.add(new POI("HIP 117078", "WP#9", galaxyService));
				this.POIS.add(new POI("Spongou FA-A e2", "WP#10", galaxyService));
				this.POIS.add(new POI("Cyuefai BC-D d12-4", "WP#11", galaxyService));
				this.POIS.add(new POI("Cyuefoo LC-D d12-0", "WP#12", galaxyService));
				this.POIS.add(new POI("Byaa Thoi EW-E d11-0", "WP#13", galaxyService));
				this.POIS.add(new POI("Byaa Thoi GC-D d12-0", "WP#14", galaxyService));
				this.POIS.add(new POI("Auzorts NR-N d6-0", "WP#15", galaxyService));
				this.POIS.add(new POI("Lyruewry BK-R d4-12", "WP#16", galaxyService));
				this.POIS.add(new POI("Hypou Chreou RS-S c17-6", "WP#17", galaxyService));
				this.POIS.add(new POI("Hypiae Brue DI-D c12-0", "WP#18", galaxyService));
				this.POIS.add(new POI("Sphiesi HX-L d7-0", "WP#19", galaxyService));
				this.POIS.add(new POI("Flyae Proae IN-S e4-1", "WP#20", galaxyService));
				this.POIS.add(new POI("Footie AA-A g0", "WP#21", galaxyService));
				this.POIS.add(new POI("Oedgaf DL-Y g0", "WP#22", galaxyService));
				this.POIS.add(new POI("Gria Bloae YE-A g0", "WP#23", galaxyService));
				this.POIS.add(new POI("Exahn AZ-S d3-8", "WP#24", galaxyService));
				this.POIS.add(new POI("Chua Eop ZC-T c20-0", "WP#25", galaxyService));
				this.POIS.add(new POI("Beagle Point", "WP#26", galaxyService));
				this.POIS.add(new POI("Cheae Eurl AA-A e0", "WP#27", galaxyService));
				this.POIS.add(new POI("Hyphielia QH-K c22-0", "WP#28", galaxyService));
				this.POIS.add(new POI("Praei Bre WO-R d4-3", "WP#29", galaxyService));
				this.POIS.add(new POI("Suvua FG-Y f0", "WP#30", galaxyService));
				this.POIS.add(new POI("Hypaa Byio ZE-A g1", "WP#31", galaxyService));
				this.POIS.add(new POI("Eembaitl DL-Y d13", "WP#32", galaxyService));
				this.POIS.add(new POI("Synookaea MX-L d7-0", "WP#33", galaxyService));
				this.POIS.add(new POI("Blea Airgh EI-B d13-1", "WP#34", galaxyService));
				this.POIS.add(new POI("Ood Fleau ZJ-I d9-0", "WP#35", galaxyService));
				this.POIS.add(new POI("Plae Eur DW-E d11-0", "WP#36", galaxyService));
				this.POIS.add(new POI("Haffner 18 LSS 27", "WP#37", galaxyService));
				this.POIS.add(new POI("Achrende", "WP#38", galaxyService));
			} catch (NonUniqueResultException e) {
				// TODO Auto-generated catch block
				throw new RuntimeException(e);
			}

			this.addMouseWheelListener(this);
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

			zsize = zoom;
			zfrom = coord.getZ() - zsize / 2;
			zto = coord.getZ() + zsize / 2;
			xsize = ((float) this.getWidth() / (float) this.getHeight()) * zsize;
			xfrom = coord.getX() - xsize / 2;
			xto = coord.getX() + xsize / 2;
			ysize = Math.min(xsize, zsize);
			yfrom = coord.getY() - ysize / 2;
			yto = coord.getY() + ysize / 2;
			int tmp = Math.max(1, Math.round(this.getHeight() / 1000f));
			final int psize = tmp % 2 == 0 ? tmp + 1 : tmp;
			final int poffset = (psize - 1) / 2;

			// My travel history
			logger.trace("Painting travel history");
			if (this.commanderData.getVisitedStarSystems().size() >= 2) {
				int toIndex = this.commanderData.getVisitedStarSystems().size();
				int fromIndex = Math.max(0, toIndex - 128);
				int alpha = 0;
				Point prev = null;
				for (int idx = fromIndex; idx < toIndex; idx++) {
					VisitedStarSystem visitedStarSystem = this.commanderData.getVisitedStarSystems().get(idx);
					Point curr = this.coordToPoint(visitedStarSystem.getCoord());
					if (prev != null) {
						alpha += 2;
						((Graphics2D) g).setStroke(new BasicStroke(2));
						g.setColor(new Color(160, 160, 160, alpha));
						g.drawLine(prev.x, prev.y, curr.x, curr.y);
					}
					prev = curr;
				}
			}

			// Known systems
			logger.trace("Painting known systems");
			try (CloseableIterator<StarSystem> stream = this.galaxyService.streamAllSystemsWithin(xfrom, xto, yfrom, yto, zfrom, zto)) {
				stream.forEachRemaining(system -> {
					Point p = this.coordToPoint(system.getCoord());
					float dy = Math.abs(system.getCoord().getY() - coord.getY());
					int alpha = 255 - Math.round((dy / (ysize / 2)) * 255);

					if (alpha > 0) {
						g.setColor(new Color(80, 80, 80, alpha));
						g.fillRect(p.x - poffset, p.y - poffset, psize, psize);
						//g.fillRect(p.x, p.y, 1, 1);
					}
				});
			}

			// Known entry stars
			logger.trace("Painting known entry stars");
			try (CloseableIterator<Body> stream = this.galaxyService.streamStarsWithin(xfrom, xto, yfrom, yto, zfrom, zto, /* isMainStar = */ Boolean.TRUE, /* starClasses = */ null)) {
				stream.forEachRemaining(mainStar -> {
					if (mainStar.getStarClass() != null) {
						Point p = this.coordToPoint(mainStar.getCoord());
						float dy = Math.abs(mainStar.getCoord().getY() - coord.getY());
						int alpha = 255 - Math.round((dy / (ysize / 2)) * 127);

						if (alpha > 0) {
							Color color = StarUtil.starClassToColor(mainStar.getStarClass());
							g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
							g.fillRect(p.x - poffset, p.y - poffset, psize, psize);
							//g.fillRect(p.x, p.y, 1, 1);
						}
					}
				});
			}

			// Other commanders
			logger.trace("Painting other commanders");
			g.setFont(new Font("Sans Serif", Font.PLAIN, 12));
			for (OtherCommanderLocation location : this.otherCommanders.values()) {
				Point p = this.coordToPoint(location.getCoord());
				float dy = Math.abs(location.getCoord().getY() - coord.getY());
				int alpha = 255 - Math.round((dy / (ysize / 2)) * 127);

				if (alpha > 0) {
					g.setColor(new Color(0, 255, 255, alpha));
					//g.fillRect(p.x - poffset, p.y - poffset, psize, psize);
					g.drawString(location.getCommanderName(), p.x, p.y);
				}
			}

			// POIs
			logger.trace("Painting POIs");
			for (POI poi : POIS) {
				Point p = this.coordToPoint(poi.getCoord());
				float dy = Math.abs(poi.getCoord().getY() - coord.getY());
				int alpha = 255 - Math.round((dy / (ysize / 2)) * 127);

				if (alpha > 0) {
					g.setColor(new Color(0, 255, 0, alpha));
					//g.fillRect(p.x - poffset, p.y - poffset, psize, psize);
					g.drawString(poi.getName(), p.x, p.y);
				}
			}

			// Scale
			g.setColor(Color.WHITE);
			g.drawString(String.format(Locale.US, "%,.0f Ly x %,.0f Ly", xsize, zsize), 5, 15);
		}

		private Point coordToPoint(Coord coord) {
			float xPercent = (coord.getX() - this.xfrom) / this.xsize;
			float yPercent = 1.0f - ((coord.getZ() - this.zfrom) / this.zsize);

			return new Point(Math.round(xPercent * this.getWidth()), Math.round(yPercent * this.getHeight()));
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			int notches = e.getWheelRotation();
			if (notches < 0) {
				for (int i = 0; i < Math.abs(notches); i++) {
					this.zoom -= 100f;
					if (this.zoom < 100f) {
						this.zoom = 100f;
						break;
					}
				}
				this.repaint();
			} else if (notches > 0) {
				for (int i = 0; i < Math.abs(notches); i++) {
					this.zoom += 100f;
					if (this.zoom > 20000f) {
						this.zoom = 20000f;
						break;
					}
				}
				this.repaint();
			}
		}

	}

	public float getVisibleDistance() {
		return (float) Math.sqrt(this.area.xsize * this.area.xsize + this.area.zsize * this.area.zsize);
	}

}
