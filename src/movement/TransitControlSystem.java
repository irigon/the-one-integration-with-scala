package movement;

import core.Coord;
import core.SettingsError;
import movement.map.MapNode;
import movement.map.SimMap;
import movement.TransitTrip.TripDirection;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

class TransitControlSystem {

	private short routeType;
    private List<TransitStop> stops;
    private volatile TreeMap<Integer, TransitTrip> schedule;
    private Random r;

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
	TransitControlSystem(String stopsFile, String scheduleFile, SimMap map, int okMapType) {
		// --- Debug
		System.out.println("Reading " + stopsFile);
		stops = readStops(stopsFile, map);
		TransitStop start = stops.get(0);
		TransitStop end = stops.get(stops.size() - 1);

		if (start.equals(end)) {
		    routeType = CIRCULAR;
        } else {
		    routeType = PINGPONG;
        }

		// Debug -- Order map neighbors from more specific to more general
		
		
		
        buildPaths(stops, okMapType);
        schedule = readSchedule(scheduleFile);
        r = new Random();
	}

	private List<TransitStop> readStops(String fileName, SimMap map) {
		boolean mirror = map.isMirrored();
		double xOffset = map.getOffset().getX();
		double yOffset = map.getOffset().getY();
		List<TransitStop> stops = new ArrayList<>();

		TransitStop previous = null;
		FileReader fr;
		try {
			fr = new FileReader(fileName);
		} catch (FileNotFoundException e) {
			throw new SettingsError("Cannot find stops file.");
		}
		try (BufferedReader br = new BufferedReader(fr)) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] columns = line.split(COMMA_DELIMITER);
				if (columns.length != 2) {
					throw new SettingsError("Malformed stops file supplied, needs two columns.");
				}
				String[] coords = columns[0].split(" ");
				if (coords.length != 2) {
					throw new SettingsError("Malformed stops file supplied, " +
							"needs two coordinate values, space separated.");
				}
				Coord c = new Coord(
						Double.valueOf(coords[0]),
						Double.valueOf(coords[1])
				);
				if (mirror) {
					c.setLocation(c.getX(), -c.getY());
				}
				c.translate(xOffset, yOffset);
				// --- debugging:
				System.out.println("Original coord : " + coords[0] + "," + coords[1] + " --> " + c.toString());

				MapNode node = map.getNodeByCoord(c);
				if (node == null) {
					throw new SettingsError("Stop "+coords[0]+", "+coords[1]+" (transformed: "+
							c.getX()+", "+c.getY()+") is not a valid Map node");
				}

				int timeTo = Integer.parseInt(columns[1]) * 60;
                // if travel takes less than one minute, than 30s
                if (timeTo == 0) timeTo = 30;

				TransitStop next = new TransitStop(node, timeTo);
				next.setPrev(previous);
				if (previous != null) {
					previous.setNext(next);
				}
				previous = next;
				stops.add(next);
			}
			if (stops.size() < 2) {
				throw new SettingsError("Malformed stops file supplied: " + fileName +
						"needs at least 2 stops to run simulation");
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e2) {
			throw new SettingsError("Malformed stops file supplied: " + fileName +
					"first column must be two double values, second column int value");
		}

		return stops;
	}

	private TreeMap<Integer, TransitTrip> readSchedule(String fileName) {
		TreeMap<Integer, TransitTrip> schedule = new TreeMap<>();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		Date timeStart = null;
		try {
			timeStart = sdf.parse("00:00:00");
		} catch (ParseException e) {
			e.printStackTrace();
		}
		FileReader fr;

		try {
			fr = new FileReader(fileName);
		} catch (FileNotFoundException e) {
			throw new SettingsError("Cannot find schedule file.");
		}
		try (BufferedReader br = new BufferedReader(fr)) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] columns = line.split(COMMA_DELIMITER);
				if (columns.length != 3) {
					throw new SettingsError("Malformed schedule file supplied, needs 3 columns.");
				}

				Date time = sdf.parse(columns[0]);
				long seconds = (time.getTime() - timeStart.getTime()) / 1000;
				int startIndex = Integer.parseInt(columns[1]);
				int endIndex = Integer.parseInt(columns[2]);
				schedule.put((int) seconds, new TransitTrip(
						(int) seconds,
						stops.get(startIndex),
						stops.get(endIndex),
						startIndex < endIndex ?
								TripDirection.FORWARD :
								TripDirection.BACKWARD
				));
			}

			if (schedule.isEmpty()) {
				schedule = null;
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			throw new SettingsError("Malformed stops file supplied, " +
					"first column must be two double values, second column int value");
		} catch (java.text.ParseException e) {
			throw new SettingsError("Can not parse time");
		} catch (IndexOutOfBoundsException e) {
			throw new SettingsError("Stop index for trip is out of bounds");
		}

		return schedule;
	}

	private MapNode next_stop_is_a_neighbor(List<MapNode> neighbors, MapNode nextStop) {
		for (MapNode n : neighbors) {
			if (n.equals(nextStop)) {
				return n;
			}
		}
		return null;
	}

	private MapNode get_next_neighbor(List<MapNode> neighbors, MapNode lastWayP, int mapType) {
		MapNode candidate = null;
		for (MapNode n : neighbors) {
			if (!n.isType(mapType) || n.equals(lastWayP)) {
				continue;
			}
			if (candidate == null) {
				candidate = n;
			} else {
				if (Integer.bitCount(n.getType()) < Integer.bitCount(candidate.getType())) {
					candidate = n;
				}
			}
		}
		assert (candidate != null);
		return candidate;
	}
	
	private void buildPaths(List<TransitStop> stops, int okMapType) {
		TransitStop currentStop = stops.get(0);
		TransitStop nextStop = currentStop.getNext();
		MapNode currentNode = currentStop.node;
		MapNode nextStopNode = nextStop.node;

		MapNode endNode = stops.get(stops.size() - 1).node;
		MapNode lastWayPoint = new MapNode(null);
		double distance = 0;
        TransitWay p = new TransitWay();

		//nodes:
		System.out.println("Stop Node:" + currentNode.toString());
		while (!currentNode.equals(endNode)) {
			p.addWaypoint(currentNode.getLocation());
			
			MapNode n = next_stop_is_a_neighbor(currentNode.getNeighbors(), nextStopNode);
			
			if (n != null) { // if nextStopNode is a neighbor...
				System.out.println("Stop Node:" + n.toString());
				//p.addWaypoint(n.getLocation());
				distance += getDistance(currentNode, n);

				p.setDuration(nextStop.timeTo);
				p.setDistance(distance);

				currentStop.setForward(p);
				nextStop.setBackward(p.reversed());

				distance = 0;
				p = new TransitWay();

				if (!n.equals(endNode)) {
					currentStop = nextStop;
					nextStop = nextStop.getNext();
					nextStopNode = nextStop.node;
				}
				lastWayPoint = currentNode;
				currentNode = n;	
			} 
			
			/**
			 * StopNode is not neighbor. Get the most specific neighbor that is not lastWayPoint
			 * If 2 neighbors belong to line 4 and a third to line 4 and 6, we should prioritize the one from line 4.
			 */
			
			else { 
				MapNode neighbor = get_next_neighbor(currentNode.getNeighbors(), lastWayPoint, okMapType);
				System.out.println("Neighbor:" + neighbor.toString() + ", endNode:" + endNode.toString());
				lastWayPoint = currentNode;
				currentNode = neighbor;
				distance += getDistance(lastWayPoint, currentNode);		
			}
			
			/*for (MapNode n : currentNode.getNeighbors()) {
				if (n.equals(nextStopNode)) {
					p.addWaypoint(n.getLocation());
					distance += getDistance(currentNode, n);

					p.setDuration(nextStop.timeTo);
					p.setDistance(distance);

					currentStop.setForward(p);
					nextStop.setBackward(p.reversed());

					distance = 0;
					p = new TransitWay();

					if (!n.equals(endNode)) {
						currentStop = nextStop;
						nextStop = nextStop.getNext();
						nextStopNode = nextStop.node;
					}
					lastWayPoint = currentNode;
					currentNode = n;
					continue nodes;
				}
				if (!n.equals(lastWayPoint) && n.isType(okMapType)) {
					lastWayPoint = currentNode;
					currentNode = n;
					distance += getDistance(lastWayPoint, currentNode);
					continue nodes;
				}
			}
			*/
		}
		System.out.println("Done.");
	}

	private double getDistance(MapNode n1, MapNode n2) {
		return n1.getLocation().distance(n2.getLocation());
	}


    public short getRouteType() {
        return routeType;
    }


    public synchronized TransitTrip getInitialTrip(int time) {
		if (schedule == null) {
			return defaultTrip(time);
		}
		if (schedule.size() == 0)
			throw new SettingsError("There is a host group that has a higher number of hosts than "+
					"trips in the respective schedule. nrofHosts must always be <= count of trips");
		int key = schedule.ceilingKey(time);
		return schedule.remove(key);
	}

	public synchronized TransitTrip getTripForStop(int time, TransitStop currentStop) {
		if (schedule == null) {
			return defaultTripForStop(time, currentStop);
		}
		Integer key = schedule.ceilingKey(time);
		while (!schedule.get(key).getFirstStop().equals(currentStop)) {
			key = schedule.higherKey(key);
			if (key == null)
				return null;
		}
		return schedule.remove(key);
	}

	private TransitTrip defaultTrip(int time) {
		if (r.nextBoolean()) {
			return new TransitTrip(
					time,
					stops.get(0),
					stops.get(stops.size()-1),
					TripDirection.FORWARD
			);
		}
		return new TransitTrip(
				time,
				stops.get(stops.size()-1),
				stops.get(0),
				TripDirection.BACKWARD
		);

	}

	private TransitTrip defaultTripForStop(int time, TransitStop currentStop) {
		TransitStop lastStop = currentStop.equals(stops.get(0)) ? stops.get(stops.size()-1) : stops.get(0);
		return new TransitTrip(
				time,
				currentStop,
				lastStop,
				TripDirection.BACKWARD //FIXME!
		);
	}
}

