package routing

import scroll.internal._
import core.Message, core.DTNHost
import java.util.HashMap
import java.util.ArrayList
import java.util.stream.Collectors

class NaiveCompartment extends AbstractCompartment{

    def compartment_name() : String = { "Naive Compartment" }

    class NaiveAlgorithmB {
        def algo_name() : String = { "Algorithm A" }
        def route(m:Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : java.util.List[DTNHost] = {
            new java.util.ArrayList[DTNHost](0);
        }
    }

    class NaiveAlgorithmA {
        def algo_name() : String = { "Epidemic Routing" }
        def route(m:Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : java.util.List[DTNHost] = {
            prop.keySet()
              .stream()
              .collect(Collectors.toList());
        }
    }

    def adapt(router : ActiveRouter, ctxt: String) : Player[ActiveRouter] = {
         router play new NaiveAlgorithmA
    }

    def route(router : Player[ActiveRouter], m: Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : java.util.List[DTNHost] = {
        router route (m,prop,me)
    }
}
