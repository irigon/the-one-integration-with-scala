package movement;

import core.Coord;

public class TransitTrip {

    private int startTime;
    private TransitStop firstStop;
    private TransitStop lastStop;
    private TransitStop currentStop;
    private TripDirection direction;

    public enum TripDirection {
        FORWARD,
        BACKWARD
    }

    public TransitTrip(int startTime, TransitStop firstStop, TransitStop lastStop, TripDirection direction) {
        this.startTime = startTime;
        this.firstStop = firstStop;
        this.lastStop = lastStop;
        this.currentStop = firstStop;
        this.direction = direction;
    }

    public TransitWay nextWay() {
        TransitWay tw = null;

        switch (direction) {
            case FORWARD:
                tw = new TransitWay(currentStop.getForward());
                currentStop = currentStop.getNext();
                break;
            case BACKWARD:
                tw = new TransitWay(currentStop.getBackward());
                currentStop = currentStop.getPrev();
                break;
        }
        return tw;
    }

    public boolean atFirstStop() {
        return currentStop.equals(firstStop);
    }

    public boolean atLastStop() {
        return currentStop.equals(lastStop);
    }

    public TransitStop getFirstStop() {
        return firstStop;
    }

    public TransitStop getLastStop() {
        return lastStop;
    }

    public TransitStop getCurrentStop() {
        return currentStop;
    }

    public Coord startLocation() {
        return firstStop.node.getLocation();
    }

    public int getStartTime() {
        return startTime;
    }
    
    public int getArrivalTime() {
    	TransitStop ts = firstStop;
    	int sum=0;
    	while (! (ts.node == lastStop.node)){
    		sum += ts.timeTo(); 		
    		ts = (direction == TripDirection.FORWARD) ? ts.getNext(): ts.getPrev();
    	} 

    	
    	sum += ts.timeTo() + getStartTime();
    	//System.out.println("Device departing at " + getStartTime() + " arrives at " + sum);
    	return sum;
    }
}
