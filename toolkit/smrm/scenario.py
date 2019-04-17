# -*- coding: utf-8 -*-
import sys, os, math
from os import path
from lib.osm import OsmRouteParser
from lib.project import Projector
from lib.one import HostGroup, ScenarioSettings
from lib import out

PARENT_DIR = '..'
DATA_DIR = 'data'
NODES_FILE = '{}_nodes.wkt'
STOPS_FILE = '{}_stops.csv'
STATIONS_FILE = 'stations.wkt'

def main(osm_file):
    scenario = basename_without_ext(osm_file)

    # read all routes (relations with tag[k="type"][v="route"])
    # from osm file
    with open(osm_file) as fp:
        orp = OsmRouteParser(fp)
    routes = orp.parse_routes()
    points = []
    for r in routes:
        points.extend(r.nodes)

    # initialize projection pane (width, height in m)
    # from coordinate bounds of all nodes
    proj = Projector(precision=2)
    width, height = proj.init_dimensions(points)

    # switch to ONE project root and make dir
    # for the new scenario in /data
    os.chdir(path.join(PARENT_DIR, PARENT_DIR))
    out_dir = path.join(DATA_DIR, scenario)
    if not path.isdir(out_dir):
        os.mkdir(out_dir)

    nodes_file = path.join(out_dir, NODES_FILE)
    stops_file = path.join(out_dir, STOPS_FILE)
    stations_file = path.join(out_dir, STATIONS_FILE)

    # begin contents of a ONE settings file for this scenario
    s = ScenarioSettings(scenario)
    stations = []

    for r in routes:
        # transform the coordinates from lat,long
        # to x,y tuples on projection pane
        nodes = proj.transform_coords(r.nodes)
        stops = proj.transform_coords(r.stops)
        name = r.name

        # write a wkt LINESTRING with all nodes in this route
        # (includes stops and way nodes)
        out.write_wkt_linestring(nodes, nodes_file.format(name))
        # write stops in csv with 1st col: stop coords,
        # 2nd col: time to each stop
        out.write_csv_stops(stops, stops_file.format(name))

        # for each route, create a host group.
        # this group will contain the moving hosts along the route.
        # nodes_file is the map file this group is ok to move on
        g = HostGroup(name)
        g.set('movementModel', 'ScheduledMapRouteMovement')
        g.set('routeFile', stops_file.format(name))
        g.set('routeType', 2)
        g.set('nrofHosts', 1)
        g.set_okmap(nodes_file.format(name))
        s.add_group(g)

        stations.extend(stops)

    # add another group for all stations.
    # in this group, for each station one stationary host
    # will be spawned to act as a fixed relay node at the platform
    out.write_wkt_points(stations, stations_file)
    g = HostGroup('S')
    g.set('movementModel', 'StationaryMultiPointMovement')
    g.set('stationarySystemNr', 1)
    g.set('pointFile', stations_file)
    g.set('nrofHosts', len(stations))
    s.add_group(g)

    # write group options and map files to settings contents
    s.complete_groups()
    s.spacer()

    # set world size to ceiled projection pane bounds
    # and adjust host address range to number of hosts
    s.set('MovementModel.worldSize', '{w}, {h}'.format(
        w=math.ceil(width),
        h=math.ceil(height)
    ))
    s.set('Events1.hosts', '{min},{max}'.format(
        min=0,
        max=len(routes) + len(stations)
    ))

    # write settings contents to file in ONE project root
    s.write('{scenario}_settings.txt'.format(
        scenario=scenario
    ))

def basename_without_ext(file: str) -> str:
    base = path.basename(file)
    base = path.splitext(base)[0]
    return str(base)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Please give .osm input file as argument")
        sys.exit(1)

    main(sys.argv[1])
