package movement;

public class TimedPath extends Path {

    private double duration;
    private double distance;

    public TimedPath() {
    }

    public TimedPath(TimedPath path) {
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

}
