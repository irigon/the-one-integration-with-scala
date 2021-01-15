package routing

import core.Message, core.DTNHost
import java.util.HashMap
import java.util.ArrayList

import scroll.internal._

class PredictiveCompartment extends AbstractCompartment {

    def compartment_name() : String = { "Predictive Compartment" }

    class AlgorithmARouter {
        def algo_name() : String = { "PredictiveAlgorithm A" }
        def route(msg : String) : String = {
            "Routing " + msg + " with " + algo_name() + " in " + compartment_name();
        }
    }

    class PRoPHET{
        def algo_name() : String = { "PRoPHET V2" }
        def route(m:Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : java.util.ArrayList[DTNHost] = {
            new java.util.ArrayList[DTNHost](0);
        }
    }

    def adapt(router : ActiveRouter, info: String) : Player[ActiveRouter] = {
         router play new PRoPHET
    }

    def route(router : Player[ActiveRouter],  m:Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : ArrayList[DTNHost] = {
        router route (m,prop,me)
    }
}
