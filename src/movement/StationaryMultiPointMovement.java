package movement;

import core.Coord;
import core.Settings;


/**
 * A Movement model with stationary (not moving) nodes, that reads node locations from points in a wkt file
 * To save memory, all node locations are saved in the StationaryMultiPointControlSystem and each host retrieves
 * one location from it.
 *
 * @author Felix Cornelius
 */
public class StationaryMultiPointMovement extends MapBasedMovement {

	private static final String POINT_FILE_S = "pointFile";
	private StationaryMultiPointControlSystem controlSystem;
	private Coord loc;

	/**
	 * Creates a new movement model based on a Settings object's settings.
	 * @param s The Settings object where the settings are read from
	 */
	public StationaryMultiPointMovement(Settings s) {
		super(s);
		int smcs = s.getInt(StationaryMultiPointControlSystem.STATIONARY_SYSTEM_NR);
		String fileName = s.getSetting(POINT_FILE_S);
		controlSystem = StationaryMultiPointControlSystem.getControlSystem(smcs, super.getMap(), fileName);
	}

	/**
	 * Copy constructor.
	 * @param smpm The StationaryMultiPointMovement prototype
	 */
	public StationaryMultiPointMovement(StationaryMultiPointMovement smpm) {
		super(smpm);
		this.controlSystem = smpm.controlSystem;
	}

	/**
	 * Returns the only location of this movement model
	 * @return the only location of this movement model
	 */
	@Override
	public Coord getInitialLocation() {
		loc = controlSystem.retrievePoint();
		return loc;
	}

	/**
	 * Returns a single coordinate path (using the only possible coordinate)
	 * @return a single coordinate path
	 */
	@Override
	public Path getPath() {
		Path p = new Path(0);
		p.addWaypoint(loc);
		return p;
	}

	@Override
	public double nextPathAvailable() {
		return Double.MAX_VALUE;	// no new paths available
	}

	@Override
	public StationaryMultiPointMovement replicate() {
		return new StationaryMultiPointMovement(this);
	}

}
