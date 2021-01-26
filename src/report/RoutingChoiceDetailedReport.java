/*
 * Copyright 2010-2012 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package report;

/**
 * Records the number of times each routing approach was chosen with format:
 * <p>
 * [Simulation time] [flooding] [probabilistic] 
 * </p>
 *
 * <p>
 * The number of times each approach was chosen is a snapshot of the last n seconds
 * as defined by the <code>occupancyInterval</code> setting
 * </p>
 *
 * @author	teemuk
 */
import java.util.List;

import core.DTNHost;
import core.Settings;
import core.SimClock;
import core.UpdateListener;
import util.Tuple;
import routing.AdaptiveRouter;


public class RoutingChoiceDetailedReport extends Report implements UpdateListener {

	/**
	 * Record the number of times a routing approach eas chosen each n second -setting id ({@value}).
	 * Defines the interval how often (seconds) a new snapshot of buffer occupancy is taken
	 */
	public static final String ROUTING_REPORT_INTERVAL = "Interval";
	/** Default value for the snapshot interval */
	public static final int DEFAULT_ROUTING_REPORT_INTERVAL = 60;

	private double lastRecord = Double.MIN_VALUE;
	private int interval;

	/**
	 * Creates a new RoutingChoiceDetailedReport instance.
	 */
	public RoutingChoiceDetailedReport() {
		super();

		Settings settings = getSettings();
		if (settings.contains(ROUTING_REPORT_INTERVAL)) {
			interval = settings.getInt(ROUTING_REPORT_INTERVAL);
		} else {
			interval = -1; /* not found; use default */
		}

		if (interval < 0) { /* not found or invalid value -> use default */
			interval = DEFAULT_ROUTING_REPORT_INTERVAL;
		}
	}

	public void updated(List<DTNHost> hosts) {
		if (SimClock.getTime() - lastRecord >= interval) {
			lastRecord = SimClock.getTime();
			printLine(hosts);
		}
	}

	/**
	 * Prints a snapshot of the average buffer occupancy
	 * @param hosts The list of hosts in the simulation
	 */
	private void printLine(List<DTNHost> hosts) {
		for (DTNHost h : hosts) {
			AdaptiveRouter r = (AdaptiveRouter)h.getRouter();
			for (String line : r.routing_strategy_counters())
				write(line);
			r.reset_routing_strategy_counters();
		}
	}

}
