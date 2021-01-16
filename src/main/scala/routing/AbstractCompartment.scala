package routing

import scroll.internal._
import core.Message
import core.DTNHost
import java.util.HashMap
import java.util.ArrayList
import java.util.List

abstract class AbstractCompartment extends Compartment {
    def adapt(router : ActiveRouter, info: String) : Player[ActiveRouter];
    def route(router : Player[ActiveRouter], m: Message, prop: HashMap[DTNHost, java.lang.Double], me: DTNHost) : java.util.List[DTNHost];
}
