package movement;

public class ScheduleService {

    private int startTime;
    private short firstStop;
    private short lastStop;
    private short currentStop;


    public ScheduleService(int startTime, short firstStop, short lastStop) {
        this.startTime = startTime;
        this.firstStop = firstStop;
        this.lastStop = lastStop;
        this.currentStop = firstStop;
    }

    public boolean isDirectionBackward() {
        return firstStop > lastStop;
    }

    public short nextStop() {
        if (isDirectionBackward())
            return currentStop--;
        return currentStop++;
    }

    public boolean atFirstStop() {
        return currentStop == firstStop;
    }

    public boolean atLastStop() {
        return currentStop == lastStop;
    }

    public short getFirstStop() {
        return firstStop;
    }

    public short getCurrentStop() {
        return currentStop;
    }

    public int getStartTime() {
        return startTime;
    }
}
