package movement;

import movement.map.MapNode;

public class ScheduledRouteStop {

    public MapNode node;
    public int timeTo;

    ScheduledRouteStop(MapNode node, int timeTo) {
        this.node = node;
        this.timeTo = timeTo;
    }

    public boolean equals(ScheduledRouteStop s) {
        if (s == this) {
            return true;
        }
        else {
            return s.node.getLocation().equals(
                    this.node.getLocation()
            );
        }
    }
}
