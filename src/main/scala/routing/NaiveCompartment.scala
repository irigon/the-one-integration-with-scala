package routing

import scroll.internal._

class NaiveCompartment extends AbstractCompartment{

    def compartment_name() : String = { "Naive Compartment" }

    class NaiveAlgorithmB {
        def algo_name() : String = { "Algorithm A" }
        def route(msg : String) : String = {
            "Routing " + msg + " with " + algo_name() + " in " + compartment_name();
        }
    }

    class NaiveAlgorithmA {
        def algo_name() : String = { "NaiveAlgorithm X" }
        def route(msg : String) : String = {
            "Routing " + msg + " with " + algo_name() + " in " + compartment_name();
        }
    }

    def adapt(router : ActiveRouter, ctxt: String) : Player[ActiveRouter] = {
         router play new NaiveAlgorithmA
    }

    def route(router : Player[ActiveRouter], msg : String) : String = {
        router route msg
    }
}
