package routing

import core.Message, core.DTNHost
import java.util.HashMap
import java.util.ArrayList
import java.util.List
import java.util.Map
import scroll.internal._
import scala.collection.JavaConversions._

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
        def route(m:Message, prop: java.util.HashMap[core.DTNHost, java.lang.Double], me: core.DTNHost) : java.util.List[core.DTNHost] = {
            // Return a list with all hosts that have a higher DP than me
            val my_val = prop.get(me)
            val l = new java.util.ArrayList[core.DTNHost]();
            //for (Map.Entry[DTNHost, java.lang.Double] entry : prop.entrySet()) {
            for (entry <- prop.entrySet) {
                if(entry.getValue() > my_val){
                    l.add(entry.getKey());
                }
            }
            l
        }
    }

    def adapt(router : ActiveRouter, info: String) : Player[ActiveRouter] = {
         router play new PRoPHET
    }

    def route(router : Player[ActiveRouter],  m:Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : List[DTNHost] = {
        router route (m,prop,me)
    }
}
