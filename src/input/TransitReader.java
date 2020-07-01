package input;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import core.Coord;
import core.SettingsError;
import core.SimError;
import movement.TransitStop;
import movement.TransitTrip;
import movement.TransitWay;
import movement.TransitTrip.TripDirection;
import movement.map.MapNode;
import movement.map.SimMap;

public class TransitReader {

    private final String COMMA_DELIMITER = ",";
    private String stopsFilename;
    private String scheduleFilename;
    private String nodesFilename;
    private SimMap map;
    private long okMapType;
    private List<TransitStop> stops;

    /*
     * We currently consider 1 stopFile and 1 scheduleFile.
     * This might change if we add alternative schedules
     */
	public TransitReader(String stopsFilename, 
			String scheduleFilename, 
			String nodesFilename, 
			SimMap map,
			long okMap) {
		this.stopsFilename = stopsFilename;
		this.scheduleFilename = scheduleFilename;
		this.nodesFilename = nodesFilename;
		this.map = map;
		this.okMapType = okMap;
		// read stops from stop file as a list of transitNodes
		try {
			this.stops = readStops();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// order the nodes, define paths and distances
		buildPaths();
	}
	

	/**
	 * Read the stop file as an ordered list of Transitstops
	 * @return An ordered list of TransitStops
	 */
	public List<TransitStop> readStops() throws IOException {
		List<String> result;
		try (Stream<String> lines = Files.lines(Paths.get(this.stopsFilename))) {
			result = lines.collect(Collectors.toList());
		}
		return coordinateListToTransitStop(result);
	}
	
	/**
	 * Read a list of coordinates and return the respective list of TransitStops
	 * @param transitStopLines: spaced separated list of coordinates
	 * @return An ordered list of TransitStops
	 */
	public List<TransitStop> coordinateListToTransitStop(List<String> transitStopLines) {
		List<TransitStop> stops = new ArrayList<>();
		List<String> line_fields;	// a list of the fields (coord, timeTo)
		List<Double> coords;
		TransitStop previous = null;
		for (String tsl: transitStopLines) {
			// split the line in [coord_as_string, timeTo]
			line_fields = Stream.of(tsl.split(","))
					.map(String::trim)
					.collect(Collectors.toList());
			
			// split cood_as_string into [(double)coord_x, (double)coord_y]
			coords = Stream.of(line_fields.get(0).split(" "))
					  .map(String::trim)
					  .map(Double::parseDouble)
					  .collect(Collectors.toList());

			// create transit stop and set links
			TransitStop next = new TransitStop(
					get_node(coords.get(0),	coords.get(1)), 
					Integer.parseInt(line_fields.get(1)));
			
			// set long TransitStops with long wait, if any
			if (line_fields.size() > 2) {
				next.setExtendedWait(Double.parseDouble(line_fields.get(2)));
			}

			next.setPrev(previous);
			if (previous != null) {
				previous.setNext(next);
			}
			previous = next;
			stops.add(next);
		}
		return stops;
	}

	/**
	 * Read a map node from its x,y coordinates
	 * @param x: x coordinate
	 * @param y; y coordinate
	 * @return MapNode of the translated coordinates from x,y
	 */

	private MapNode get_node(double x, double y) {
		MapNode node;
		Coord c = new Coord(x, y);
		
		// translate and mirror if needed
		updateCoordinate(this.map, c);
		
		node = this.map.getNodeByCoord(c);
		if (node == null) {
			throw new SettingsError("Stop " + x + ", " + y + " (transformed: "+
					c.getX()+", "+c.getY()+") is not a valid Map node");
		}
		return node;
	}

	/**
	 * Translate coordinates according to the map settings (offset + mirroring)
	 * @param map: Scenario map
	 * @param c: coordinates
	 * @return translated coordinate
	 */
	private void updateCoordinate(SimMap map, Coord c) {
		double xOffset = map.getOffset().getX();
		double yOffset = map.getOffset().getY();

		if (map.isMirrored()) {
			c.setLocation(c.getX(), -c.getY());
		}
		c.translate(xOffset, yOffset);
	}

	
	/**
	 * Read the schedules from file and transform in a treeMap.
	 * @return TreeMap with the departure time as key and an array of 
	 * transit_trips as value
	 */
	public TreeMap<Integer, ArrayList<TransitTrip>> readSchedule() {
		TreeMap<Integer, ArrayList<TransitTrip>> schedule = new TreeMap<>();
		
		TransitTrip tr_curr_line;
		int depart_time;

		List<String> line_list = new ArrayList<String>();
		try (Stream<String> lines = Files.lines(Paths.get(this.scheduleFilename))) {
			line_list = lines.collect(Collectors.toList());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		for (String line: line_list) {
			tr_curr_line = getTripFromLine(line);
			depart_time = tr_curr_line.getStartTime();

			// if key not in map, add list
			if (schedule.get(depart_time) == null) {
				schedule.put(depart_time, new ArrayList<TransitTrip>());
			}
			
			schedule.get(depart_time).add(tr_curr_line);
		}
		
		if (schedule.isEmpty()) {
			schedule = null;
		}
		
		return schedule;
	}
	
	/**
	 * Transform the line input into a TransitTrip 
	 * @param line read from file
	 * @return TransitTrip
	 */
	TransitTrip getTripFromLine(String line) {
		TransitTrip result_tt;
		SimpleDateFormat sdf = new SimpleDateFormat("dd:mm:HH:mm:ss");
		String[] columns = line.split(COMMA_DELIMITER);
		Date timeStart = null;

		// default time, 01.06.2020, Monday
		try {
			timeStart = sdf.parse("01:06:00:00:00");
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if (columns.length != 3) {
			throw new SettingsError("Malformed schedule file supplied, needs 3 columns.");
		}

		Date time = null;
		try {
			time = sdf.parse(columns[0]);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		int seconds = (int)((time.getTime() - timeStart.getTime()) / 1000);
		int startIndex = Integer.parseInt(columns[1]);
		int endIndex = Integer.parseInt(columns[2]);
		TripDirection td = startIndex < endIndex ? TripDirection.FORWARD : TripDirection.BACKWARD;
		
		return (new TransitTrip(seconds, this.stops.get(startIndex), this.stops.get(endIndex), td));
	}

	/**
	 * Read a trip from node files. Return it as an ordered list of nodes
	 * @param fileName
	 * @param map
	 * @return An ordered list of TransitStops
	 */

	List<MapNode> readPath(List<TransitStop> stops) {
		SimMap mapFromDisk;

		List<MapNode> nodeList = new ArrayList<MapNode>();
		List<Coord> coordList = new ArrayList<Coord>();
		
		WKTMapReader r = new WKTMapReader(true);
		try {
			coordList = r.loadPathAsList(new File(this.nodesFilename), (int)this.okMapType);
		} catch (IOException e) {
			throw new SimError(e.toString(),e);
		}
		
		int counter=0;
		int len = coordList.size();
		//System.out.println("Reading path...");
		for (Coord c: coordList) {
			//if ((counter++)%(len/10)==0) {System.out.print("*");}
			updateCoordinate(map, c);
			MapNode n = map.getNodeByCoord(c);
			if (n != null) {
				nodeList.add(n);
			} else {
				throw new SettingsError("Stop " + c + " is not a valid Map node");
			}			
		}
		
		MapNode startNode = stops.get(0).node;

		if (!startNode.getLocation().equals(coordList.get(0))) {
			System.out.println("Error - inicial node not found in nodes file.");
			System.exit(1);
		}
		
		MapNode lastNode = stops.get(stops.size()-1).node;
		if (!lastNode.getLocation().equals(coordList.get(coordList.size() - 1))) {
			System.out.println("Error - final node not found in nodes file.");
			System.exit(1);
		}
		
		return nodeList;
	}

	
	/**
	 * 
	 * @param stops
	 * @param map 
	 * @param okMapType
	 * 
	 * Read the node order from file in order to ensure the order is respected
	 * We need to create a path to p_forward and one to p_backwards, since they have different nodes.
	 * p_forward starts at index 1 and ends at the next stop
	 * p_backwards starts at departure station and end at the last node before next station,
	 * this way, the backward direction will work the same as the forward one.
	 */
	
	private void buildPaths() {
		TransitStop currentStop = this.stops.get(0);
		TransitStop nextStop = currentStop.getNext();
		MapNode currentNode = currentStop.node;
		MapNode nextStopNode = nextStop.node;
		List<MapNode> orderedPath = readPath(this.stops);

		MapNode endNode = this.stops.get(this.stops.size() - 1).node; 
		MapNode lastWayPoint = new MapNode(null);
		double distance = 0;
        TransitWay p = new TransitWay();

		int index = 0;
		while (!currentNode.equals(endNode)) {
			p.addWaypoint(currentNode.getLocation());
			MapNode nextNode = orderedPath.get(index+1);
			distance += getDistance(currentNode, nextNode);
			// neighbor is nextStopNode
			if (nextNode.equals(nextStopNode)) {
				p.setDuration(currentStop.timeTo());
				p.setDistance(distance);
				
				//currentStop.setForward(p);
				p.addWaypoint(nextNode.getLocation());
				currentStop.setForward(p);
				
				nextStop.setBackward(p.reversed());

				distance = 0;
				p = new TransitWay();

				if (!nextNode.equals(endNode)) {
					currentStop = nextStop;
					nextStop = nextStop.getNext();
					nextStopNode = nextStop.node;
				}
			}
				
			lastWayPoint = currentNode;
			currentNode = nextNode;
			index += 1;
		}
	}
	
	private double getDistance(MapNode n1, MapNode n2) {
		return n1.getLocation().distance(n2.getLocation());
	}
	
	public List<TransitStop> getStops() {
		return this.stops;
	}


}
