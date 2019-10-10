package routing

import scroll.internal._

class PredictiveCompartment extends AbstractCompartment {

    def compartment_name() : String = { "Predictive Compartment" }

    class AlgorithmARouter {
        def algo_name() : String = { "PredictiveAlgorithm A" }
        def route(msg : String) : String = {
            "Routing " + msg + " with " + algo_name() + " in " + compartment_name();
        }
    }

    class PredictiveAlgorithmB{
        def algo_name() : String = { "PredictiveAlgorithm B" }
        def route(msg : String) : String = {
            "Routing " + msg + " with " + algo_name() + " in " + compartment_name();
        }
    }

    def adapt(router : ActiveRouter, info: String) : Player[ActiveRouter] = {
         router play new PredictiveAlgorithmB
    }

    def route(router : Player[ActiveRouter], msg : String) : String = {
        router route msg
    }
}
