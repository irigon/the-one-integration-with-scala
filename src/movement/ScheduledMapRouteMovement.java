/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import core.Coord;
import core.Settings;
import core.SimClock;
import movement.map.MapNode;
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

	/** Per node group schedule containing route time information */
	public static final String SCHEDULE_FILE_S = "scheduleFile";

	private ScheduledMapRouteControlSystem system;
	private short currentStopIndex;
	private short routeType;
	private double waitTime = 0;
	private ScheduleService currentService;

	/**
	 * Creates a new movement model based on a Settings object's settings.
	 * @param settings The Settings object where the settings are read from
	 */
	public ScheduledMapRouteMovement(Settings settings) {
		super(settings);
		String stopsFile = settings.getSetting(ROUTE_FILE_S);
		String scheduleFile = settings.getSetting(SCHEDULE_FILE_S);

		system = new ScheduledMapRouteControlSystem(
				stopsFile,
				scheduleFile,
                getMap(),
                getOkMapNodeTypes()[0]
        );
		routeType = system.getRouteType();

	}

	/**
	 * Copyconstructor. Gives a route to the new movement model from the
	 * list of routes and randomizes the starting position.
	 * @param proto The ScheduledMapRouteMovement prototype
	 */
	protected ScheduledMapRouteMovement(ScheduledMapRouteMovement proto) {
		super(proto);
		this.currentStopIndex = proto.currentStopIndex;
		this.system = proto.system;
		this.routeType = proto.routeType;
	}

    @Override
    public Path getPath() {
        TimedPath tp = system.nextPath(currentService);
	    tp.adjustSpeed(waitTime);
	    return tp;
    }

    @Override
	public double nextPathAvailable() {
		if (currentService.atFirstStop()) {
			waitTime = 0;
			return currentService.getStartTime();
		}
		if (currentService.atLastStop()) {
			currentService = system.getServiceForStop(
					(int) SimClock.getTime(),
					currentService.getCurrentStop()
			);
			waitTime = 0;
			// When no more services from this stop exist, halt here.
			if (currentService == null)
				return Double.MAX_VALUE;
			return currentService.getStartTime();
		}
		waitTime = generateWaitTime();
		return SimClock.getTime() + waitTime;
	}

	/**
	 * Returns the first stop on the route
	 */
	@Override
	public Coord getInitialLocation() {
		currentService = system.getInitialService((int) SimClock.getTime());
		return system.getInitialLocation(currentService.getFirstStop());
	}

	@Override
	public ScheduledMapRouteMovement replicate() {
		return new ScheduledMapRouteMovement(this);
	}

	@Override
	protected void checkMapConnectedness(List<MapNode> nodes) {
		// map needs not to be connected, since it is combined out of all route maps
	}
}


