/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import core.Coord;
import core.Settings;
import core.SettingsError;
import movement.map.DijkstraPathFinder;
import movement.map.MapNode;
import movement.map.MapRoute;
import movement.map.SimMap;

import java.util.List;

/**
 * Map based movement model that uses predetermined paths within the map area.
 * Other than MapRouteMovement, this movement model uses fixed paths to get from one stop to another
 * (no pathfinder). Additionally, a schedule for when each node should arrive at which route stop is respected.
 * See toolkit/smrm/README.md for details and instructions on how to generate needed files.
 */
public class ScheduledMapRouteMovement extends MapBasedMovement implements
	SwitchableMovement {

	/** Per node group setting used for selecting a route file ({@value}) */
	public static final String ROUTE_FILE_S = "routeFile";
	/**
	 * Per node group setting used for selecting a route's type ({@value}).
	 * Integer value from {@link MapRoute} class.
	 */
	public static final String ROUTE_TYPE_S = "routeType";

	/**
	 * Per node group setting for selecting which stop (counting from 0 from
	 * the start of the route) should be the first one. By default, or if a
	 * negative value is given, a random stop is selected.
	 */
	public static final String ROUTE_FIRST_STOP_S = "routeFirstStop";

	/** Prototype's reference to all routes read for the group */
	private List<MapRoute> allRoutes = null;
	/** next route's index to give by prototype */
	private Integer nextRouteIndex = null;
	/** Index of the first stop for a group of nodes (or -1 for random) */
	private int firstStopIndex = -1;

	/** Route of the movement model's instance */
	private MapRoute route;

	/** The last waypoint of the previous route before stop node */
	private MapNode lastWayPoint;

	/**
	 * Creates a new movement model based on a Settings object's settings.
	 * @param settings The Settings object where the settings are read from
	 */
	public ScheduledMapRouteMovement(Settings settings) {
		super(settings);
		String fileName = settings.getSetting(ROUTE_FILE_S);
		int type = settings.getInt(ROUTE_TYPE_S);
		allRoutes = MapRoute.readRoutes(fileName, type, getMap());
		nextRouteIndex = 0;

		this.route = this.allRoutes.get(this.nextRouteIndex).replicate();
		if (this.nextRouteIndex >= this.allRoutes.size()) {
			this.nextRouteIndex = 0;
		}

		this.firstStopIndex = 0;

		if (settings.contains(ROUTE_FIRST_STOP_S)) {
			this.firstStopIndex = settings.getInt(ROUTE_FIRST_STOP_S);
			if (this.firstStopIndex >= this.route.getNrofStops()) {
				throw new SettingsError("Too high first stop's index (" +
						this.firstStopIndex + ") for route with only " +
						this.route.getNrofStops() + " stops");
			}
		}
	}

	/**
	 * Copyconstructor. Gives a route to the new movement model from the
	 * list of routes and randomizes the starting position.
	 * @param proto The ScheduledMapRouteMovement prototype
	 */
	protected ScheduledMapRouteMovement(ScheduledMapRouteMovement proto) {
		super(proto);
		this.route = proto.allRoutes.get(proto.nextRouteIndex).replicate();
		this.firstStopIndex = proto.firstStopIndex;

		if (firstStopIndex < 0) {
			/* set a random starting position on the route */
			this.route.setNextIndex(rng.nextInt(route.getNrofStops()-1));
		} else {
			/* use the one defined in the config file */
			this.route.setNextIndex(this.firstStopIndex);
		}

		proto.nextRouteIndex++; // give routes in order
		if (proto.nextRouteIndex >= proto.allRoutes.size()) {
			proto.nextRouteIndex = 0;
		}
	}

	@Override
	public Path getPath() {
		Path p = new Path(generateSpeed());
		MapNode to = route.nextStop();
		MapNode next = lastMapNode;
		while (!next.equals(to)) {
			p.addWaypoint(next.getLocation());
			List<MapNode> ns = next.getNeighbors();
			for (MapNode n : ns) {
				if (n.isType(getOkMapNodeTypes()) && !n.equals(lastWayPoint)) {
					lastWayPoint = next;
					next = n;
					break;
				}
			}
			if (next.equals(lastMapNode)) {
				// last stop on route reached
				next = lastWayPoint;
				lastWayPoint = lastMapNode;
			}
		}

		// to reached
		p.addWaypoint(next.getLocation());
		lastMapNode = next;
		return p;
	}

	@Override
	public double nextPathAvailable() {
		return super.nextPathAvailable();
	}

	/**
	 * Returns the first stop on the route
	 */
	@Override
	public Coord getInitialLocation() {
		if (lastMapNode == null) {
			lastMapNode = route.nextStop();
		}

		return lastMapNode.getLocation().clone();
	}

	@Override
	public Coord getLastLocation() {
		if (lastMapNode != null) {
			return lastMapNode.getLocation().clone();
		} else {
			return null;
		}
	}


	@Override
	public ScheduledMapRouteMovement replicate() {
		return new ScheduledMapRouteMovement(this);
	}

	/**
	 * Returns the list of stops on the route
	 * @return The list of stops
	 */
	public List<MapNode> getStops() {
		return route.getStops();
	}
}
