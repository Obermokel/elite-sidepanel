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

import borg.ed.sidepanel.commander.CommanderData;
import borg.ed.sidepanel.commander.OtherCommanderLocation;
import borg.ed.sidepanel.commander.VisitedStarSystem;
import borg.ed.universe.constants.Element;
import borg.ed.universe.constants.PlanetClass;
import borg.ed.universe.constants.StarClass;
import borg.ed.universe.constants.TerraformingState;
import borg.ed.universe.data.Coord;
import borg.ed.universe.exceptions.NonUniqueResultException;
import borg.ed.universe.model.Body;
import borg.ed.universe.model.Body.MaterialShare;
import borg.ed.universe.model.StarSystem;
import borg.ed.universe.service.UniverseService;
import borg.ed.universe.util.BodyUtil;
import borg.ed.universe.util.MiscUtil;
import borg.ed.universe.util.StarUtil;

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

	public void updateFromElasticsearch(boolean repaintMap) {
		final Coord coord = this.commanderData.getCurrentCoord();
		if (coord == null) {
			return;
		}

		logger.trace("Searching for known bodies in " + this.commanderData.getCurrentStarSystem());
		List<Body> knownBodies = this.universeService.findBodiesByStarSystemName(this.commanderData.getCurrentStarSystem());
		this.txtKnownBodies.setText(knownBodies.stream() //
				.filter(b -> !b.getName().toLowerCase().contains("belt")) //
				.sorted((b1, b2) -> b1.getName().toLowerCase().compareTo(b2.getName().toLowerCase())) //
				.map(b -> b.getName().replace(b.getStarSystemName(), "").trim()) //
				.map(name -> StringUtils.isEmpty(name) ? "MAIN" : name) //
				.collect(Collectors.joining(", ")));

		logger.trace("Searching for valuable bodies in " + this.commanderData.getCurrentStarSystem());
		this.txtValuableBodies.setText(knownBodies.stream() //
				.filter(b -> BodyUtil.estimatePayout(b) >= 10_000) //
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
		LinkedHashMap<String, Long> valuableSystems = this.findNearbyValuableSystems(coord, /* range = */ 500f, this.commanderData);
		int counter = 0;
		for (String systemName : valuableSystems.keySet()) {
			float distance = 0f;
			try {
				StarSystem starSystem = this.universeService.findStarSystemByName(systemName);
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
					.filter(sh -> Element.POLONIUM.equals(sh.getName()) || Element.YTTRIUM.equals(sh.getName()) || Element.NIOBIUM.equals(sh.getName())
							|| Element.ARSENIC.equals(sh.getName())) //
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

			try (CloseableIterator<Body> stream = this.universeService.streamStarsNear(coord, range, /* isMainStar = */ Boolean.TRUE,
					Arrays.asList(StarClass.N))) {
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

			Page<Body> page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(g5, nio, ars, cad, ger, van),
					PageRequest.of(0, 100));
			while (page != null) {
				for (Body body : page.getContent()) {
					result.add(body);
				}
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(g5, nio, ars, cad, ger, van), page.nextPageable());
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
			Page<Body> page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(pol, nio, ars), PageRequest.of(0, 100));
			while (page != null) {
				for (Body body : page.getContent()) {
					if (!result.contains(body)) {
						result.add(body);
					}
				}
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
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
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
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
	private LinkedHashMap<String, Long> findNearbyValuableSystems(final Coord coord, final float range, CommanderData commanderData) {
		LinkedHashMap<String, Long> valueBySystem = new LinkedHashMap<>();

		try {
			logger.trace("Searching for valuable systems " + range + " Ly around " + coord);

			final long maxDistanceFromArrival = 10_000L; // Ls
			final long minValue = 1_000_000L; // CR

			Set<String> starSystemNames = new HashSet<>();

			List<PlanetClass> elwWwAw = Arrays.asList(PlanetClass.EARTHLIKE_BODY, PlanetClass.WATER_WORLD, PlanetClass.AMMONIA_WORLD);
			Page<Body> page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ null, elwWwAw, PageRequest.of(0, 100));
			while (page != null) {
				starSystemNames.addAll(
						page.getContent().stream().map(Body::getStarSystemName).filter(name -> StringUtils.isNotEmpty(name)).collect(Collectors.toList()));
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ null, elwWwAw, page.nextPageable());
				} else {
					page = null;
				}
			}

			page = this.universeService.findPlanetsNear(coord, range, /* isTerraformingCandidate = */ Boolean.TRUE, null, PageRequest.of(0, 100));
			while (page != null) {
				starSystemNames.addAll(
						page.getContent().stream().map(Body::getStarSystemName).filter(name -> StringUtils.isNotEmpty(name)).collect(Collectors.toList()));
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
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
						systemPayout += BodyUtil.estimatePayout(body.getStarClass(), body.getPlanetClass(),
								TerraformingState.TERRAFORMABLE.equals(body.getTerraformingState()));
					}
				}

				if (systemPayout >= minValue) {
					boolean skip = false;
					for (VisitedStarSystem vss : commanderData.getVisitedStarSystems()) {
						if (vss.getName().equals(starSystemName)) {
							skip = true; // Assume already scanned
							break;
						}
					}
					StarSystem starSystem = this.universeService.findStarSystemByName(starSystemName);
					if (starSystem != null && starSystem.getPopulation() != null && starSystem.getPopulation().longValue() > 0) {
						skip = true; // Public knowledge
					}

					if (!skip) {
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
			Page<Body> page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(pol), PageRequest.of(0, 100));
			while (page != null) {
				for (Body body : page.getContent()) {
					if (StringUtils.isNotEmpty(body.getStarSystemName())) {
						poloniumSystemNames.add(body.getStarSystemName());
					}
				}
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(pol), page.nextPageable());
				} else {
					page = null;
				}
			}

			// Yttrium
			page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(ytt), PageRequest.of(0, 100));
			while (page != null) {
				for (Body body : page.getContent()) {
					if (StringUtils.isNotEmpty(body.getStarSystemName())) {
						yttriumSystemNames.add(body.getStarSystemName());
					}
				}
				if (page.hasNext() && page.getNumber() + 1 < 10000 / page.getSize()) {
					page = this.universeService.findPlanetsHavingElementsNear(coord, range, Arrays.asList(ytt), page.nextPageable());
				} else {
					page = null;
				}
			}

			// Intersection
			Set<String> systemNames = new HashSet<>(poloniumSystemNames);
			systemNames.retainAll(yttriumSystemNames);

			for (String systemName : systemNames) {
				StarSystem starSystem = this.universeService.findStarSystemByName(systemName);
				if (starSystem != null) {
					Map<Element, BigDecimal> totalMaterials = this.sumMaterialsOfSystem(systemName);
					BigDecimal polonium = totalMaterials.getOrDefault(Element.POLONIUM, BigDecimal.ZERO);
					BigDecimal yttrium = totalMaterials.getOrDefault(Element.YTTRIUM, BigDecimal.ZERO);
					BigDecimal cadmium = totalMaterials.getOrDefault(Element.CADMIUM, BigDecimal.ZERO);
					BigDecimal niobium = totalMaterials.getOrDefault(Element.NIOBIUM, BigDecimal.ZERO);
					BigDecimal arsenic = totalMaterials.getOrDefault(Element.ARSENIC, BigDecimal.ZERO);
					BigDecimal germanium = totalMaterials.getOrDefault(Element.GERMANIUM, BigDecimal.ZERO);
					BigDecimal vanadium = totalMaterials.getOrDefault(Element.VANADIUM, BigDecimal.ZERO);

					if (polonium.floatValue() >= 1.5 && yttrium.floatValue() >= 2 && cadmium.floatValue() > 0 && niobium.floatValue() > 0
							&& arsenic.floatValue() > 0 && germanium.floatValue() > 0 && vanadium.floatValue() > 0) {
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

		List<Body> bodies = this.universeService.findBodiesByStarSystemName(systemName);
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

	public static class Area extends JPanel implements MouseWheelListener {

		private static final long serialVersionUID = 8383226308842901529L;

		private final CommanderData commanderData;
		private final Map<String, OtherCommanderLocation> otherCommanders;

		private UniverseService universeService = null;

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

			this.universeService = appctx.getBean(UniverseService.class);

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
			try (CloseableIterator<StarSystem> stream = this.universeService.streamAllSystemsWithin(xfrom, xto, yfrom, yto, zfrom, zto)) {
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
			try (CloseableIterator<Body> stream = this.universeService.streamStarsWithin(xfrom, xto, yfrom, yto, zfrom, zto, /* isMainStar = */ Boolean.TRUE,
					/* starClasses = */ null)) {
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
