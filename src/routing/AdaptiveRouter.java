/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.Settings;
import core.Connection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public AdaptiveRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
        initialize_local_variables();
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
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}

		// then try any/all message to any/all connection
		//this.tryAllMessagesToAllConnections();
		if (connectedAndReady()) {
			// choose a random context and call the role that prints it.
			String curr_ctxt = ctxt_list.get(rand.nextInt(ctxt_list.size()));
			aCompartment = ca.activate(this, curr_ctxt);
			adaptedRouter = aCompartment.adapt(this, curr_ctxt);
			aCompartment.route(adaptedRouter, "MyTestMesg");
		}
	}


	@Override
	public AdaptiveRouter replicate() {
		return new AdaptiveRouter(this);
	}

	private void initialize_local_variables(){
		ca = new CompartmentSwitcher();
		rand = new Random();
		ctxt_list = new ArrayList<String>();
		ctxt_list.add("naive_ctxt");
		ctxt_list.add("predictive_ctxt");
		ctxt_list.add("scheduled_ctxt");
	}

	// Return true if node is connected and at least one message is available
	public Boolean connectedAndReady() {
		List<Connection> connections = getConnections();
		return (connections.size() > 0 && this.getNrofMessages() > 0);
	}

}
