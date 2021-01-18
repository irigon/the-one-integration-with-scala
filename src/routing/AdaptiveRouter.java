/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.*;

import java.util.*;

import routing.util.RoutingInfo;
import scroll.internal.Compartment;
import scroll.internal.Compartment.Player;
import util.Tuple;

/**
 * Epidemic message router with drop-oldest buffer and only single transferring
 * connections at a time.
 */
public class AdaptiveRouter extends ActiveRouter {

	CompartmentSwitcher ca;
	AbstractCompartment aCompartment;
	Player adaptedRouter;
	//ArrayList<String> ctxt_list;
	Random rand;



	public static final double PEncMax = 0.5;
	public static final double I_TYP = 1800;
	public static final double DEFAULT_BETA = 0.9;
	public static final double DEFAULT_GAMMA = 0.999885791;
	public static final double DEFAULT_WARMING_UP = 3600.0;
	Random randomGenerator = new Random();
	public static final String ADAPTIVE_NS = "AdaptiveRouter";
	public static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";
	public static final String BETA_S = "beta";
	public static final String GAMMA_S = "gamma";
	public static final String WARMING_UP_S = "warmingUp";
	private int secondsInTimeUnit;
	private double beta;
	private double gamma;
	private double warmingUp;
	private HashMap<DTNHost, Double> preds;
	private Map<DTNHost, Double> lastEncouterTime;
	private double lastAgeUpdate;
	private Collection<DTNHost> scheduleSet;

	// counters
	private int flood;
	private int probabilistic;


	// return a snapshot of the counters
	public Tuple<Integer, Integer> routing_strategy_counters(){
		Tuple<Integer, Integer> current_values = new Tuple<Integer, Integer> (flood, probabilistic);
		reset_routing_strategy_counters();
		return current_values;
	}

	// reset the flooding and probabilistic counters
	public void reset_routing_strategy_counters(){
		flood = 0;
		probabilistic = 0;
	}

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public AdaptiveRouter(Settings s) {
		super(s);
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

		if (adaptiveSettings.contains(WARMING_UP_S)) {
			warmingUp = adaptiveSettings.getDouble(WARMING_UP_S);
		}
		else {
			warmingUp = DEFAULT_WARMING_UP;
		}
		initialize_local_variables();
		initEncTimes();
	}

	/**
	 * Initializes lastEncouterTime hash
	 */

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected AdaptiveRouter(AdaptiveRouter r) {
		super(r);
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		this.beta = r.beta;
		this.gamma = r.gamma;
		this.warmingUp = r.warmingUp;
		initialize_local_variables();
		initEncTimes();
	}

	private void initEncTimes() {
		this.lastEncouterTime = new HashMap<DTNHost, Double>();
	}

	@Override
	public void changedConnection(Connection con) {
		if (con.isUp()) {
			DTNHost otherHost = con.getOtherNode(getHost());
			updateDeliveryPredFor(otherHost);
			updateTransitivePreds(otherHost);
		}
	}

	private void updateDeliveryPredFor(DTNHost host) {
		double PEnc;
		double simTime = SimClock.getTime();
		double lastEncTime=getEncTimeFor(host);
		if(lastEncTime==0)
			PEnc=PEncMax;
		else
			if((simTime-lastEncTime)<I_TYP)
			{
				PEnc=PEncMax*((simTime-lastEncTime)/I_TYP);
			}
			else
				PEnc=PEncMax;

		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * PEnc;
		preds.put(host, newValue);
		lastEncouterTime.put(host, simTime);
	}


	public double getEncTimeFor(DTNHost host) {
		if (lastEncouterTime.containsKey(host)) {
			return lastEncouterTime.get(host);
		}
		else {
			return 0;
		}
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
		assert otherRouter instanceof AdaptiveRouter : "AdaptiveRouter only works " +
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
			if(pNew>pOld)
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
		update_node_set();
		if (!canStartTransfer() ||isTransferring()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}

		tryOtherMessages();
	}

	// during the first x hours learn the path
	// After the learning phase, meeting with known devices results in predictive routing, otherwise epidemic
	private String get_context(List<Connection> connections) {
		DTNHost h = getHost();
		for (Connection c: connections)	{
			DTNHost other = c.getOtherNode(h);
			if (scheduleSet.contains(other) == false) {
				return "naive_ctxt";
			}
		}
		return "predictive_ctxt";
	}

	private Tuple<Message, Connection> tryOtherMessages() {
		List<Tuple<Message, Connection>> messagesToSend = new ArrayList<Tuple<Message, Connection>>();
		List<Message> messages = new ArrayList<Message>(getMessageCollection());
		// choose a random context
		// select available messages ordered by queue mode
		// and call the role based on context to make the routing decision
		List<Connection> connections = getConnections();
		if (connectedAndReady()) {
			this.sortByQueueMode(messages);
		    // chose a context
			List<Connection> active_connections = getConnections();
			// select context
        	String curr_ctxt = get_context(active_connections);
        	//System.out.println("Contexto: " + curr_ctxt);
			aCompartment = ca.activate(this, curr_ctxt);
			adaptedRouter = aCompartment.adapt(this, curr_ctxt);
			// get active connections
			for (Message m : messages) {
				List candidates = aCompartment.route(adaptedRouter, m, preds, this.getHost());
				//System.out.println(this.getHost() + ", flood:" + flood + ", prob:" + probabilistic);
				// send message to the active connections that are present in the candidates set
				for (Connection con : active_connections) {
					DTNHost other = con.getOtherNode(getHost());
					AdaptiveRouter othRouter = (AdaptiveRouter)other.getRouter();
					if (othRouter.isTransferring()) {
						continue; // skip hosts that are transferring
					}
					if (othRouter.hasMessage(m.getId())) {
						continue; // skip messages that the other one has
					}
					messagesToSend.add(new Tuple<Message, Connection>(m,con));
				}
			}
			if (messagesToSend.size() == 0) {
				return null;
			}
			// increment the counter to the routing type responsible to forward this message
			if (curr_ctxt == "naive_ctxt") {
				System.out.println("Host " + getHost() + " chose naive");
				flood++;
			} else {
				probabilistic++;
			}
		}
		// sort the message-connection tuples
		Collections.sort(messagesToSend, new AdaptiveRouter.TupleComparator());
		return tryMessagesForConnected(messagesToSend);	// try to send messages
	}

	/**
	 * Comparator for Message-Connection-Tuples that orders the tuples by
	 * their delivery probability by the host on the other side of the
	 * connection (GRTRMax)
	 */
	private class TupleComparator implements Comparator
			<Tuple<Message, Connection>> {

		public int compare(Tuple<Message, Connection> tuple1,
						   Tuple<Message, Connection> tuple2) {
			// delivery probability of tuple1's message with tuple1's connection
			double p1 = ((AdaptiveRouter)tuple1.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple1.getKey().getTo());
			// -"- tuple2...
			double p2 = ((AdaptiveRouter)tuple2.getValue().
					getOtherNode(getHost()).getRouter()).getPredFor(
					tuple2.getKey().getTo());

			// bigger probability should come first
			if (p2-p1 == 0) {
				/* equal probabilities -> let queue mode decide */
				return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
			}
			else if (p2-p1 < 0) {
				return -1;
			}
			else {
				return 1;
			}
		}
	}

	@Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(preds.size() +
				" delivery prediction(s)");

		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();

			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
					host, value)));
		}

		top.addMoreInfo(ri);
		return top;
	}

	@Override
	public AdaptiveRouter replicate() {
		return new AdaptiveRouter(this);
	}

	private void initialize_local_variables(){
		ca = new CompartmentSwitcher();
		rand = new Random();
		//ctxt_list = new ArrayList<String>(List.of("naive_ctxt","predictive_ctxt"));
		this.preds = new HashMap<DTNHost, Double>();
		this.scheduleSet = new HashSet<DTNHost>();
		flood = 0;
		probabilistic = 0;
	}

	// Return true if node is connected and at least one message is available
	public Boolean connectedAndReady() {
		List<Connection> connections = getConnections();
		return (connections.size() > 0 && this.getNrofMessages() > 0);
	}

	public void update_node_set() {
		List<Connection> connections = getConnections();
		DTNHost h = getHost();
		for (Connection c: connections)	{
			DTNHost other = c.getOtherNode(h);
			if (scheduleSet.contains(other) == false && SimClock.getTime() < warmingUp) {
					scheduleSet.add(other);
			}
		}
	}
}
