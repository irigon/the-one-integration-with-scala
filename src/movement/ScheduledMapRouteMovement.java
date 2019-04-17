/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import core.Coord;
import core.Settings;
import core.SettingsError;
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

    public static final String DIRECTION_S = "direction";

	public static final String ROUTE_FIRST_STOP_S = "routeFirstStop";


	private ScheduledMapRouteControlSystem system;
	private short currentStopIndex;
	private short direction = -1;
	private short nrofStops;
	private double waitTime = 0;

	/**
	 * Creates a new movement model based on a Settings object's settings.
	 * @param settings The Settings object where the settings are read from
	 */
	public ScheduledMapRouteMovement(Settings settings) {
		super(settings);
		String fileName = settings.getSetting(ROUTE_FILE_S);

		system = new ScheduledMapRouteControlSystem(
		        fileName,
                getMap(),
                getOkMapNodeTypes()[0]
        );
		nrofStops = system.getNrOfStops();

        if (settings.contains(DIRECTION_S)) {
            direction = (short) settings.getInt(DIRECTION_S);
            if (direction != 0 || direction != 1) {
                throw new SettingsError("Invalid route direction set.");
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
		this.currentStopIndex = proto.currentStopIndex;
		this.system = proto.system;
		this.nrofStops = proto.nrofStops;

		if (proto.direction != -1) {
		    this.direction = proto.direction;
        } else {
            this.direction = system.getInitialDirection();
        }
	}

    @Override
    public Path getPath() {
	    if (currentStopIndex == nrofStops - 1) {
            currentStopIndex = 0;
            direction = (short)((direction + 1) % 2); // if ping pong
        }
        TimedPath tp = system.getPath(direction, currentStopIndex++);
	    tp.adjustSpeed(waitTime);
	    return tp;
    }

    @Override
	public double nextPathAvailable() {
		// if (currentStopIndex == nrofStops - 1) (at end station)
		// check schedule for next start

		waitTime = generateWaitTime();
		return SimClock.getTime() + waitTime;
	}

	/**
	 * Returns the first stop on the route
	 */
	@Override
	public Coord getInitialLocation() {
		return system.getInitialLocation(direction);
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


