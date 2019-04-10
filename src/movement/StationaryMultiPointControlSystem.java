package movement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import core.SettingsError;
import input.WKTReader;
import movement.map.MapNode;
import movement.map.SimMap;
import core.Coord;
import core.DTNSim;

/**
 * This class controls the node locations of all hosts in the group. It exists only once and reads stationary
 * node locations on initialization. Each host in the group can then retrieve a location and {@link #availablePoints}
 * is updated (synchronized).
 *
 * @author Felix Cornelius
 */
public class StationaryMultiPointControlSystem {
	public static final String STATIONARY_SYSTEM_NR = "stationarySystemNr";

	private static volatile HashMap<Integer, StationaryMultiPointControlSystem> systems;
	private List<Coord> availablePoints;
	private SimMap simMap;

	static {
		DTNSim.registerForReset(StationaryMultiPointControlSystem.class.getCanonicalName());
		reset();
	}

	/**
	 * Creates a new instance of StationaryMultiPointControlSystem
	 *
	 */
	private StationaryMultiPointControlSystem(SimMap simMap, String fileName) {
		this.simMap = simMap;
		availablePoints = this.readPoints(fileName, simMap);
	}

	public static void reset() {
		systems = new HashMap<Integer, StationaryMultiPointControlSystem>();
	}

	/**
	 * Returns a reference to a StationaryMultiPointControlSystem with ID provided as parameter.
	 * If a system does not already exist with the requested ID, a new one is
	 * created.
	 *
	 * @param systemID unique ID of the system
	 * @return The StationaryMultiPointControlSystem with the provided ID
	 */
	public static StationaryMultiPointControlSystem getControlSystem(int systemID, SimMap simMap, String fileName) {
		Integer id = systemID;

		if (!systems.containsKey(id)) {
			synchronized (StationaryMultiPointControlSystem.class) {
				if (!systems.containsKey(id)) {
					StationaryMultiPointControlSystem smcs =
							new StationaryMultiPointControlSystem(simMap, fileName);
					systems.put(id, smcs);
				}
			}
		}

		return systems.get(id);
	}


	public synchronized Coord retrievePoint() {
		if (!availablePoints.isEmpty()) {
			return availablePoints.remove(0);
		}
		System.out.println("No more available Points to retrieve!");
		return new Coord(0,0);
	}

	private List<Coord> readPoints(String fileName, SimMap map) {
		WKTReader reader = new WKTReader();
		boolean mirror = map.isMirrored();
		double xOffset = map.getOffset().getX();
		double yOffset = map.getOffset().getY();

		File pointFile;
		List<Coord> coords;
		try {
			pointFile = new File(fileName);
			coords = reader.readPoints(pointFile);
		} catch (IOException ioe) {
			throw new SettingsError("Couldn't read Point-data from file '" +
					fileName + " (cause: " + ioe.getMessage() + ")");
		}

		if (coords.size() == 0) {
			throw new SettingsError("Read a Point group of size 0 from " + fileName);
		}

		for (Coord c : coords) {
			if (mirror) { // mirror Points if map data is also mirrored
				c.setLocation(c.getX(), -c.getY()); // flip around X axis
			}
			// translate to match map data
			c.translate(xOffset, yOffset);
		}

		return coords;
	}
}
