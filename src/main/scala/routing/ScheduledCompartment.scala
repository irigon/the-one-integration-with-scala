package routing

import core.Message
import core.DTNHost
import java.util.HashMap
import java.util.ArrayList

import scroll.internal._

class ScheduledCompartment extends AbstractCompartment {

    def compartment_name() : String = { "Scheduled Compartment" }

    class ScheduledAlgorithm{
        def algo_name() : String = { "ScheduledAlgorithm A" }
        def route(m:Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : java.util.ArrayList[DTNHost] = {
            new java.util.ArrayList[DTNHost](0);
        }
    }

    class AlgorithmBRouter {
        def algo_name() : String = { "Algorithm B" }
        def route(m:Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : java.util.ArrayList[DTNHost] = {
            new java.util.ArrayList[DTNHost](0);
        }
    }

    def adapt(router : ActiveRouter, info: String) : Player[ActiveRouter] = {
        router play new ScheduledAlgorithm
    }

    def route(router : Player[ActiveRouter],  m:Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : ArrayList[DTNHost] = {
        router route (m,prop,me)

    }
}
