package movement;

import java.util.Collections;

public class TransitWay extends Path {

    private double duration;
    private double distance;

    public TransitWay() {
    }

    public TransitWay(TransitWay path) {
        super(path);
        this.duration = path.duration;
        this.distance = path.distance;
    }

    public double getDuration() {
        return duration;
    }

    public double getDistance() {
        return distance;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public void adjustSpeed(double waitingTime) {
        setSpeed(distance/(duration - waitingTime));
    }

    public TransitWay reversed() {
        TransitWay reversed = new TransitWay(this);
        Collections.reverse(reversed.getCoords());
        return reversed;
    }

}
