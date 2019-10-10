package routing

import scroll.internal._

abstract class AbstractCompartment extends Compartment {
    def adapt(router : ActiveRouter, info: String) : Player[ActiveRouter];
    def route(router : Player[ActiveRouter], msg : String) : String ;
}
