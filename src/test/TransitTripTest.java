package test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import core.Settings;
import core.SimScenario;
import core.World;
import input.TransitReader;
import movement.TransitControlSystem;
import movement.TransitTrip;

class TransitTripTest {
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

    public Collection<ArrayList<TransitTrip>> get_trip_lists(String conf_file) {
		TransitReader t_reader;
        int number_schedules=0;
        int num_lines=0;
		Settings.addSettings(conf_file);

		this.stopsFile = s.getSetting(ROUTE_FILE_S);
		this.scheduleFile = s.getSetting(SCHEDULE_FILE_S);
		// Create scenario and world
		SimScenario.reset();
		this.scen = SimScenario.getInstance();

		int nodeType = Integer.parseInt(s.getSetting(NODE_TYPE_S));
		s.setNameSpace("MapBasedMovement");
		String nodesFile = s.getSetting(NODES_FILE_S);

		t_reader = new TransitReader(stopsFile, scheduleFile, nodesFile, this.scen.getMap(), nodeType);
		// return the first schedule found
		return t_reader.readSchedule().values();
    	
    }
    
	@Test
	void testTripTime1() {
		Collection<ArrayList<TransitTrip>> tripLists;
        tripLists = get_trip_lists("test_configurations/trip_tests/settings1.txt");
        int counter = 0;
        ArrayList<Integer> results = new ArrayList<Integer>();
        
        for (int i: Arrays.asList(90, // departure == arrival --> 60 and waiting time --> 30 
        		120, 	// departure == arrival --> 60 + 60 waiting time --> 120
        		210,	// departure at 120 == arrival + 90 seconds
        		210,	// other direction 
        		510,	// depart == 300 + 30+60+60+60
        		510		// other direction 
        		)) {
        	results.add(i);
        }
        for (ArrayList<TransitTrip> al: tripLists) {
        	for (TransitTrip t: al) {
        		int arrival = t.getArrivalTime();
        		int expected = results.get(counter);
        		assertEquals(arrival, expected);
        		counter++;
        	}
        }
	}

}
