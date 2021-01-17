package test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import core.Coord;
import core.DTNHost;
import core.DTNSim;
import core.Settings;
import core.SimScenario;
import core.World;
import input.TransitReader;
import movement.MapBasedMovement;
import movement.MovementModel;
import movement.TransitMapMovement;
import movement.TransitStop;
import movement.TransitTrip;
import movement.map.SimMap;
import movement.TransitControlSystem;

class TransitControlSystemTest {
	
	private TestSettings s;
	private SimScenario scen;
	private World world;
	private TransitControlSystem tcs; 
	private TransitReader transit_reader;
	private String stopsFile;
	private String scheduleFile;
	public static final String ROUTE_FILE_S = "routeFile";
	public static final String SCHEDULE_FILE_S = "scheduleFile";
	public static final String NODES_FILE_S = "mapFile1";
	public static final String NODE_TYPE_S = "okMaps";

	
    @BeforeEach
	public void setUp() throws Exception {
		s = new TestSettings();
		s.setNameSpace("Group1");
		s.setSecondaryNamespace("Group");
		Settings.addSettings("test_configurations/default_settings.txt");
	}

    /**
     * The schedule must have the same number of lines that the file
     * @throws IOException 
     */
	@Test
	void testReadSchedule() throws IOException {
		TransitReader t_reader;
		TreeMap<Integer, ArrayList<TransitTrip>> schedule;
        int number_schedules=0;
        int num_lines=0;
		Settings.addSettings("test_configurations/hsl_line1/hsl1_simplificada_settings.txt");

		this.stopsFile = s.getSetting(ROUTE_FILE_S);
		this.scheduleFile = s.getSetting(SCHEDULE_FILE_S);
		// Create scenario and world
		this.scen = SimScenario.getInstance();

		int nodeType = Integer.parseInt(s.getSetting(NODE_TYPE_S));
		s.setNameSpace("MapBasedMovement");
		String nodesFile = s.getSetting(NODES_FILE_S);

		t_reader = new TransitReader(stopsFile, scheduleFile, nodesFile, this.scen.getMap(), nodeType);
        schedule = t_reader.readSchedule();
        
        for (ArrayList al: schedule.values()) {
        	number_schedules += al.size();
        }
        // Count number of lines in the schedule file
        Path path = Paths.get(scheduleFile);
        long lineCount = Files.lines(path).count();		
        assertEquals(number_schedules, Files.lines(path).count());
	}

	TransitControlSystem get_schedule(String s_file){
		TransitControlSystem tcs;
		Settings.addSettings(s_file);
		this.stopsFile = s.getSetting(ROUTE_FILE_S);
		this.scheduleFile = s.getSetting(SCHEDULE_FILE_S);
		// Create scenario and world
		this.scen.reset();
		this.scen = SimScenario.getInstance();
		this.world = this.scen.getWorld();

		int nodeType = Integer.parseInt(s.getSetting(NODE_TYPE_S));
		s.setNameSpace("MapBasedMovement");
		String nodesFile = s.getSetting(NODES_FILE_S);

		tcs = new TransitControlSystem(
				stopsFile, 
				scheduleFile,
				nodesFile,
				this.scen.getMap(),
				nodeType
				);		
		return tcs;
	}
	
	@Test
	void testSetTripsPerVehicle() {
		ArrayList<LinkedList<TransitTrip>> tpm;
		TransitControlSystem tcs;
        tcs = get_schedule("test_configurations/schedule_tests/settings1.txt");
        tpm = tcs.get_tripsPerMobile();
        assertEquals(tpm.size(), 1);
        assertEquals(tpm.get(0).size(),6);
	}

	
	@Test
	void testSetTripsPerVehicle2() {
		ArrayList<LinkedList<TransitTrip>> tpm;
		TransitControlSystem tcs;
        tcs = get_schedule("test_configurations/schedule_tests/settings2.txt");
        tpm = tcs.get_tripsPerMobile();
        //assertEquals(tpm.size(), 1);
        //assertEquals(tpm.get(0).size(),6);

	}

	
	@Test
	void testReadPath() {
		
	}
	
	@Test
	void buildPaths() {
		
	}
	
	@Test
	void getDistance() {
		
	}

	@Test
	void getRoutingType() {
		
	}
	
	@Test 
	void getInitialTrip() {
		
	}
	
	@Test
	void getTripForStop() {
		
	}
	
	@Test
	void defaultTrip() {
		
	}
	
	@Test
	void defaultTripForStop() {
		
	}
}
