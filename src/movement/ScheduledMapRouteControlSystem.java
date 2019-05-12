package movement;

import core.Coord;
import core.SettingsError;
import movement.map.MapNode;
import movement.map.SimMap;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

class ScheduledMapRouteControlSystem {

	private short routeType;
	private List<TimedPath> pathsForward;
    private List<TimedPath> pathsBackward;
    private List<ScheduledRouteStop> stops;
    private volatile TreeMap<Integer, ScheduleService> schedule;
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
	ScheduledMapRouteControlSystem(String stopsFile, String scheduleFile, SimMap map, int okMapType) {
		pathsForward = new ArrayList<>();
        pathsBackward = new ArrayList<>();
		stops = readStops(stopsFile, map);
		ScheduledRouteStop start = stops.get(0);
		ScheduledRouteStop end = stops.get(stops.size() - 1);

		if (start.equals(end)) {
		    routeType = CIRCULAR;
        } else {
		    routeType = PINGPONG;
        }

        buildPaths(stops, okMapType);
        schedule = readSchedule(scheduleFile);
        r = new Random();
	}

	private List<ScheduledRouteStop> readStops(String fileName, SimMap map) {
		boolean mirror = map.isMirrored();
		double xOffset = map.getOffset().getX();
		double yOffset = map.getOffset().getY();

		List<ScheduledRouteStop> stops = new ArrayList<>();
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

				MapNode node = map.getNodeByCoord(c);
				if (node == null) {
					throw new SettingsError("Stop "+coords[0]+", "+coords[1]+" (transformed: "+
							c.getX()+", "+c.getY()+") is not a valid Map node");
				}

				int timeTo = Integer.parseInt(columns[1]) * 60;
				stops.add(new ScheduledRouteStop(node, timeTo));
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

	private TreeMap<Integer, ScheduleService> readSchedule(String fileName) {
		TreeMap<Integer, ScheduleService> schedule = new TreeMap<>();
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

				schedule.put((int) seconds, new ScheduleService(
						(int) seconds,			      // start time in seconds
						Short.parseShort(columns[1]), // first stop index
						Short.parseShort(columns[2])  // last stop index
				));
			}

			if (schedule.isEmpty()) {
				schedule = null;
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e2) {
			throw new SettingsError("Malformed stops file supplied, " +
					"first column must be two double values, second column int value");
		} catch (java.text.ParseException e) {
			throw new SettingsError("Can not parse time");
		}

		return schedule;
	}

	private void buildPaths(List<ScheduledRouteStop> stops, int okMapType) {
		int nextStopIdx = 0;
		MapNode currentNode = stops.get(nextStopIdx).node;
		nextStopIdx++;
		MapNode nextStop = stops.get(nextStopIdx).node;
		MapNode endStation = stops.get(stops.size() - 1).node;
		MapNode lastWayPoint = new MapNode(null);
		double distance = 0;
        TimedPath p = new TimedPath();

		nodes:
		while (!currentNode.equals(endStation)) {
			p.addWaypoint(currentNode.getLocation());

			for (MapNode n : currentNode.getNeighbors()) {
				if (n.equals(nextStop)) {
					p.addWaypoint(n.getLocation());
					distance += getDistance(currentNode, n);

					p.setDuration(stops.get(nextStopIdx).timeTo);
					p.setDistance(distance);

					pathsForward.add(p);
					distance = 0;
					p = new TimedPath();

					if (!n.equals(endStation)) {
						nextStopIdx++;
						nextStop = stops.get(nextStopIdx).node;
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
		}
		buildBackwardsPaths(pathsForward);
	}

	private void buildBackwardsPaths(List<TimedPath> pathsForward) {
	    for (TimedPath p : pathsForward) {
	        List<Coord> coords = new ArrayList<>(p.getCoords());
            Collections.reverse(coords);
            TimedPath p2 = new TimedPath();
            for (Coord c : coords) {
                p2.addWaypoint(c);
            }
            p2.setDuration(p.getDuration());
            p2.setDistance(p.getDistance());
            pathsBackward.add(p2);
        }
    }

	private double getDistance(MapNode n1, MapNode n2) {
		return n1.getLocation().distance(n2.getLocation());
	}

	public TimedPath nextPath(ScheduleService service) {
        if (service.isDirectionBackward())
            return new TimedPath(pathsBackward.get(service.nextStop() - 1));

        return new TimedPath(pathsForward.get(service.nextStop()));
    }

    public short getRouteType() {
        return routeType;
    }

    public short getNrOfStops() {
	    return (short) stops.size();
    }

    public Coord getInitialLocation(int stopId) {
	    return stops.get(stopId).node.getLocation().clone();
    }

    public synchronized ScheduleService getInitialService(int time) {
		if (schedule == null) {
			return defaultService(time);
		}
		int key = schedule.ceilingKey(time);
		return schedule.remove(key);
	}

	public synchronized ScheduleService getServiceForStop(int time, int currentStop) {
		if (schedule == null) {
			return defaultServiceForStop(time, currentStop);
		}
		Integer key = schedule.ceilingKey(time);
		while (schedule.get(key).getFirstStop() != currentStop) {
			key = schedule.higherKey(key);
			if (key == null)
				return null;
		}
		return schedule.remove(key);
	}

	private ScheduleService defaultService(int time) {
		if (r.nextBoolean()) {
			return new ScheduleService(
					time,
					(short) 0,
					(short) (stops.size()-1)
			);
		}
		return new ScheduleService(
				time,
				(short) (stops.size()-1),
				(short) 0
		);

	}

	private ScheduleService defaultServiceForStop(int time, int currentStop) {
		int lastStop = currentStop == 0 ? stops.size() -1 : 0;
		return new ScheduleService(
				time,
				(short) currentStop,
				(short) lastStop
		);
	}
}