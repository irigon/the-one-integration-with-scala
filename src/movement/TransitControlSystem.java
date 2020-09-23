package movement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.TreeMap;

import core.SettingsError;
import input.TransitReader;
import movement.TransitTrip.TripDirection;
import movement.map.SimMap;

public class TransitControlSystem {

	private short routeType;
    private List<TransitStop> stops;
    private volatile TreeMap<Integer, ArrayList<TransitTrip>> schedule;

    // tripsPerMobile[mobileId][departure times]
    private ArrayList<LinkedList<TransitTrip>> tripsPerMobile = new ArrayList<LinkedList<TransitTrip>>();
    private Random r;
    private TransitReader t_reader;
    private int device_id;
    String scheduleFile;

    /** Type of the route ID: circular ({@value}).
     * After reaching the last node on path, the next node is the first node */
    public static final short CIRCULAR = 1;
    /** Type of the route ID: ping-pong ({@value}).
     * After last node on path, the direction on path is reversed */
    public static final short PINGPONG = 2;

    private final String COMMA_DELIMITER = ",";

	/**
	 * Creates a new movement model based on a Settings object's settings.
	 */
	public TransitControlSystem(String stopsFile, String scheduleFile, String nodesFile, SimMap map, long okMapType) {
		int num_vehicles=0;
		this.scheduleFile = scheduleFile;
		this.t_reader = new TransitReader(stopsFile, scheduleFile, nodesFile, map, okMapType);
		stops = this.t_reader.getStops();
		TransitStop start = stops.get(0);
		TransitStop end = stops.get(stops.size() - 1); 

		if (start.equals(end)) {
		    routeType = CIRCULAR;
        } else {
		    routeType = PINGPONG;
        }

		// read schedule (s) and alternatives to the schedule list
        schedule = this.t_reader.readSchedule();
        num_vehicles = setTripsPerVehicle();
        System.out.println(num_vehicles + " in line defined by file " + scheduleFile);
        
        // the schedule is distributed over mobile nodes
        r = new Random();
        
        // Device_id is used as identifier to order the trips
        // Every host receives an id that defines the trips it takes part.
        device_id = 0;
	}
	
    public short getRouteType() {
        return routeType;
    }

    public synchronized TransitTrip getInitialTrip(int dev_id) {
    	// if there are more devices then necessary, a dummy trip that will never happen
    	if (dev_id >= tripsPerMobile.size()) {
    		System.out.println("WARNING: device number: " + dev_id + ". It seems there are more devices than necessary on schedule " + scheduleFile);
    		TransitStop ts = TransitStop.dummy();
    		return new TransitTrip(2147483647, ts, ts, TransitTrip.TripDirection.BACKWARD);
    	}
    	return tripsPerMobile.get(dev_id).removeFirst();
	}

    
	public synchronized TransitTrip getTripForStop(int time, TransitStop currentStop, int dev_id) {
    	TransitTrip trip;
    	
    	if (tripsPerMobile.get(dev_id).isEmpty())
    		return null;
		
    	trip = tripsPerMobile.get(dev_id).removeFirst();

		assert (trip.getStartTime() >= time);
    	assert (currentStop.node.getLocation().equals(trip.startLocation()));
		return trip;
	}
	
	
	/**
	 * Based on the schedule, define the list of trips that each mobile device may serve.
	 * @return An ordered list of TransitStops
	 */
	public int setTripsPerVehicle() {
	    TreeMap<Integer, ArrayList<TransitTrip>> schedule_copy = (TreeMap<Integer, ArrayList<TransitTrip>>) schedule.clone();
	    int device_int_id = 0;

	    //System.out.println("Calculating devices per trip: ");
	    while (!schedule_copy.isEmpty()) {
	    	serve_trips_with_mobile(device_int_id, schedule_copy);
		    device_int_id++;
		    //System.out.print("*");	    
	    }	
	    return device_int_id;
	}
		
	/**
	 * Define all trips a single mobile can take responsibility.
	 * It does not need an inicial station or time, since it will take any available.
	 * @param startTrip: first trip to be served by this vehicle
	 * @param device_id: id to order the vehicle to the trips queue (tripsPerMobile)
	 * @param schedule: shallow copy of the schedule with the remaining trips to be served
	 * @return An ordered list of TransitStops
	 */
	public int serve_trips_with_mobile(int device_id, TreeMap<Integer, ArrayList<TransitTrip>> schedule_copy) {
		
		int num_served_trips=0;
	    int depart_after;
		TransitStop depart_from;
		
    	tripsPerMobile.add(new LinkedList<TransitTrip>());
		// get the first trip	
		// TODO: after testing, use the original schedule
		TransitTrip currentTrip = pop_from_schedule(-1, null, schedule_copy);
				//schedule_copy.remove(schedule_copy.firstKey());
		
		while (currentTrip != null) {
    		// add trip to the trip list of this vehicle
    		tripsPerMobile.get(device_id).add(currentTrip);		
    		
    		// search the next trip that departs after the arrival of this trip from the arrival station
			depart_after = currentTrip.getArrivalTime();
			depart_from = currentTrip.getLastStop();
    		//currentTrip = getTripForStopAtSchedule(depart_after, depart_from, schedule_copy);
			currentTrip = pop_from_schedule(depart_after, depart_from, schedule_copy);
    	}
		return num_served_trips;
	}
	
	/**
	 * Every vehicle has an identifier that is used to order which trips it will serve.
	 * @return the next available id
	 */
	public int getNewId() {
		return device_id++;
	}
	
	/**
	 * Exclude the first entry of the tree after "at_time" departing from station
	 * if the depart station does not matter, from_station=null
	 * @param from_station
	 * @param to_station
	 * @param at_time
	 * @return
	 */
	private TransitTrip pop_from_schedule(int at_time, TransitStop from_station, TreeMap<Integer, ArrayList<TransitTrip>> d_schedule) {
		TransitTrip ttrip = null;
		
		int time = at_time;
		int candidate_index = -1;
		Map.Entry<Integer,ArrayList<TransitTrip>> entry = d_schedule.ceilingEntry(time);
		
		// no more schedules after "at_time"
		if (entry == null) { 
			return null; 
		}
		
		// In the first trip the station does not matter
		// Take the first trip as candidate
		if (from_station == null) {
			candidate_index = 0;
		} else {		
			// this is not the first trip of the vehicle.
			// Find the next available trip departing from station it is currently located
			while (entry != null) {
				// entry.key == departing time, entry.value() List of trips departing at this time.
				candidate_index = get_trip_index(from_station, entry.getValue());
				if (candidate_index != -1) {
					break;
				}
				// no candidate found in this line, try next
				entry = d_schedule.higherEntry(entry.getKey());
			}	
		}
		
		if (entry != null) {
			ttrip = entry.getValue().remove(candidate_index);
			if (entry.getValue().size() == 0) {
				d_schedule.remove(entry.getKey());
			}
		}

		return ttrip;
	}


	/**
	 * Given a list of TransitTrip, return the index of the one that 
	 * departs from location loc
	 * @return
	 */
	private int get_trip_index(TransitStop from_station, ArrayList<TransitTrip> tripList) {
		int counter = 0;
		for (TransitTrip t: tripList) {
			if (t.startLocation().equals(from_station.node.getLocation())) {
				return counter;
			}
			counter++;
		}
		return -1;
	}
	
	// Public to test
	public TreeMap<Integer, ArrayList<TransitTrip>> get_schedule(){
		return this.schedule;
	}

	public ArrayList<LinkedList<TransitTrip>> get_tripsPerMobile(){
		return this.tripsPerMobile;
	}
}

