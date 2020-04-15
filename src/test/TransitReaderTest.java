package test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import core.Settings;
import core.SimScenario;
import core.World;
import input.TransitReader;
import movement.TransitControlSystem;
import movement.TransitStop;

class TransitReaderTest {
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
		Settings.addSettings("test_configurations/hsl_simplificada_settings.txt");

		this.stopsFile = s.getSetting(ROUTE_FILE_S);
		this.scheduleFile = s.getSetting(SCHEDULE_FILE_S);
		// Create scenario and world
		this.scen = SimScenario.getInstance();
		this.world = this.scen.getWorld();

		int nodeType = Integer.parseInt(s.getSetting(NODE_TYPE_S));
		s.setNameSpace("MapBasedMovement");
		String nodesFile = s.getSetting(NODES_FILE_S);

		this.transit_reader = new TransitReader(
				stopsFile, 
				scheduleFile,
				nodesFile,
				this.scen.getMap(),
				nodeType
				);
	}
	@Test
	void testTransitReader() {
		fail("Not yet implemented");
	}

	@Test
	void testCoordinateListToTransitStop() throws IOException {
		List<String> stop_lines = Arrays.asList(
			"1598.88 7105.9,30",
			"1822.03 6584.19,60",
			"1783.67 5965.34,60",
			"1491.47 5458.05,120",
			"1748.59 5147.14,60",
			"2291.42 4698.38,120",
			"2845.73 4155.88,120",
			"3302.67 3935.32,60",
			"3641.18 3722.39,120",
			"3960.28 3550.19,60",
			"4329.62 3432.27,120",
			"4666.88 3338.53,120",
			"4903.79 3258.79,120",
			"4970.82 2908.86,120",
			"5298.58 2746.41,60",
			"5776.46 2652.67,60",
			"6136.39 2656.91,60",
			"6411.2 2596.26,120",
			"6859.88 2501.25,60",
			"7237.87 2455.01,60",
			"7561.71 2391.39,60",
			"7913.07 2225.12,120");
		List<TransitStop> ts1 = transit_reader.coordinateListToTransitStop(stop_lines);
		List<TransitStop> ts2 = transit_reader.readStops();
		
		// compare lists of transit_stops:

		assertEquals(ts1.size(), ts2.size());
		for (int counter=0; counter < ts1.size(); counter++) {
			assertEquals(ts1.get(counter).node.getLocation(), ts2.get(counter).node.getLocation());
		}
	}

	@Test
	void testReadSchedule() {
		fail("Not yet implemented");
	}

	@Test
	void testGetStops() {
		fail("Not yet implemented");
	}

}
