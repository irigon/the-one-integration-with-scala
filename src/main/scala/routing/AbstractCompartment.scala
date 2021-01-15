package routing

import scroll.internal._
import core.Message
import core.DTNHost
import java.util.HashMap
import java.util.ArrayList

abstract class AbstractCompartment extends Compartment {
    def adapt(router : ActiveRouter, info: String) : Player[ActiveRouter];
    def route(router : Player[ActiveRouter], m: Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : java.util.ArrayList[DTNHost];
}
