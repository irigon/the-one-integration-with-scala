package movement;

import movement.map.MapNode;

public class ScheduledRouteStop {

    public MapNode node;
    public int timeTo;

    ScheduledRouteStop(MapNode node, int timeTo) {
        this.node = node;
        this.timeTo = timeTo;
    }
}
