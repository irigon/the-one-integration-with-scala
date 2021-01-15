/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.*;

import java.util.*;

import scroll.internal.Compartment;
import scroll.internal.Compartment.Player;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class AdaptiveRouter extends ActiveRouter {

	CompartmentSwitcher ca;
	AbstractCompartment aCompartment;
	Player adaptedRouter;
	ArrayList<String> ctxt_list;
	Random rand;

	// From PRoPHET
	public static final double P_INIT = 0.75;
	public static final double DEFAULT_BETA = 0.25;
	public static final double DEFAULT_GAMMA = 0.98;
	public static final String ADAPTIVE_NS = "AdaptiveRouter";
	public static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";
	public static final String BETA_S = "beta";
	public static final String GAMMA_S = "gamma";
	private int secondsInTimeUnit;
	private double beta;
	private double gamma;
	HashMap<DTNHost, Double> preds;
	private double lastAgeUpdate;

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public AdaptiveRouter(Settings s) {
		super(s);
        initialize_local_variables();
		Settings adaptiveSettings = new Settings(ADAPTIVE_NS);
		secondsInTimeUnit = adaptiveSettings.getInt(SECONDS_IN_UNIT_S);
		if (adaptiveSettings.contains(BETA_S)) {
			beta = adaptiveSettings.getDouble(BETA_S);
		}
		else {
			beta = DEFAULT_BETA;
		}

		if (adaptiveSettings.contains(GAMMA_S)) {
			gamma = adaptiveSettings.getDouble(GAMMA_S);
		}
		else {
			gamma = DEFAULT_GAMMA;
		}
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected AdaptiveRouter(AdaptiveRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
		initialize_local_variables();
	}

	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);

		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
		}
	}

	/**
	 * Updates delivery predictions for a host.
	 * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
	 * @param host The host we just met
	 */
	private void updateDeliveryPredFor(DTNHost host) {
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		preds.put(host, newValue);
	}

	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */
	public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); // make sure preds are updated before getting
		if (preds.containsKey(host)) {
			return preds.get(host);
		}
		else {
			return 0;
		}
	}

	/**
	 * Updates transitive (A->B->C) delivery predictions.
	 * <CODE>P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA
	 * </CODE>
	 * @param host The B host who we just met
	 */
	private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof ProphetRouter : "PRoPHET only works " +
				" with other routers of same type";

		double pForHost = getPredFor(host); // P(a,b)
		Map<DTNHost, Double> othersPreds =
				((AdaptiveRouter)otherRouter).getDeliveryPreds();

		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; // don't add yourself
			}

			double pOld = getPredFor(e.getKey()); // P(a,c)_old
			double pNew = pOld + ( 1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}

	/**
	 * Ages all entries in the delivery predictions.
	 * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of
	 * time units that have elapsed since the last time the metric was aged.
	 * @see #SECONDS_IN_UNIT_S
	 */
	private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) /
				secondsInTimeUnit;

		if (timeDiff == 0) {
			return;
		}

		double mult = Math.pow(gamma, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue()*mult);
		}

		this.lastAgeUpdate = SimClock.getTime();
	}

	/**
	 * Returns a map of this router's delivery predictions
	 * @return a map of this router's delivery predictions
	 */
	private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); // make sure the aging is done
		return this.preds;
	}
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}

		// choose a random context
		// select available messages ordered by queue mode
		// and call the role based on context to make the routing decision
		if (connectedAndReady()) {
			String curr_ctxt = ctxt_list.get(rand.nextInt(ctxt_list.size()));
			aCompartment = ca.activate(this, curr_ctxt);
			adaptedRouter = aCompartment.adapt(this, curr_ctxt);
			ArrayList<Message> messages = new ArrayList<Message>(this.getMessageCollection());
			this.sortByQueueMode(messages);
			String properties = "";
			for (Message m : messages) {
				//aCompartment.route(adaptedRouter, m, properties);
				//System.out.println(aCompartment.route(adaptedRouter, m, preds, this.getHost()));
				ArrayList a = aCompartment.route(adaptedRouter, m, preds, this.getHost());
            }

			// get active connections
			List<Connection> active_connections = getConnections();
			//System.out.println(aCompartment.route(adaptedRouter, "MyTestMesg"));
		}
	}

	@Override
	public AdaptiveRouter replicate() {
		return new AdaptiveRouter(this);
	}

	private void initialize_local_variables(){
		ca = new CompartmentSwitcher();
		rand = new Random();
		ctxt_list = new ArrayList<String>(List.of("naive_ctxt","predictive_ctxt","scheduled_ctxt"));
		this.preds = new HashMap<DTNHost, Double>();
	}

	// Return true if node is connected and at least one message is available
	public Boolean connectedAndReady() {
		List<Connection> connections = getConnections();
		return (connections.size() > 0 && this.getNrofMessages() > 0);
	}
}
