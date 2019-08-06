# -*- coding: utf-8 -*-
import sys, os, math
from os import path
from pathlib import Path
from lib.osm import OsmRouteParser
from lib.project import Projector
from lib.one import HostGroup, ScenarioSettings
from lib.gtfs import GTFSReader
from lib import writer
from lib.commons import TransitRoute
from typing import List

DATA_DIR = 'data'
NODES_FILE = '{}_nodes.wkt'
STOPS_FILE = '{}_stops.csv'
SCHEDULE_FILE = '{}_schedule.csv'
STATIONS_FILE = 'stations.wkt'
NR_OF_HOSTS = 1
HOST_ID_DELIM = '_'

def osm_routes(gtfs: GTFSReader, osm_file) -> List[TransitRoute]:
    # read all routes (relations with tag[k="type"][v="route"])
    # from osm file
    with open(osm_file) as fp:
        orp = OsmRouteParser(fp)
    routes = orp.parse_routes()
    ref_routes = [(r.name, r.first, r.last, len(r.stops)) for r in routes]

    gtfs.set_ref_trips(ref_routes)
    return routes

def shape_routes(gtfs: GTFSReader) -> List[TransitRoute]:
    gtfs.build_ref_trips()
    route_names = gtfs.route_names()
    paths = gtfs.shape_paths()
    stops = gtfs.shape_stops()

    routes = []
    for r in route_names:
        routes.append(
            TransitRoute(
                name=r,
                nodes=paths[r],
                stops=stops[r]
            )
        )
    return routes

def main(osm_file, gtfs_file):
    scenario = basename_without_ext(osm_file)

    gtfs = GTFSReader(gtfs_file)

    routes = osm_routes(gtfs, osm_file)
    routes = shape_routes(gtfs)

    schedule = gtfs.schedule(weekday_type=0, max_exceptions=180)
    durations = gtfs.trip_durations()

    points = set()
    for r in routes:
        points.update(r.nodes)

    # initialize projection pane (width, height in m)
    # from coordinate bounds of all nodes
    proj = Projector(precision=2)
    width, height = proj.init_dimensions(points)

    # switch to ONE project root and make dir
    # for the new scenario in /data
    one_dir = Path.cwd().parent.parent
    out_dir = path.join(one_dir, DATA_DIR, scenario)
    if not path.isdir(out_dir):
        os.mkdir(out_dir)

    nodes_file = path.join(out_dir, NODES_FILE)
    stops_file = path.join(out_dir, STOPS_FILE)
    schedule_file = path.join(out_dir, SCHEDULE_FILE)
    stations_file = path.join(out_dir, STATIONS_FILE)

    # begin contents of a ONE settings file for this scenario
    s = ScenarioSettings(scenario)
    stations = set()

    for r in routes:
        # transform the coordinates from lat,long
        # to x,y tuples on projection pane
        nodes = proj.transform_coords(r.nodes)
        stops = proj.transform_coords(r.stops)

        name = r.name
        stations.update(stops)

        # for each route, create a host group.
        # this group will contain the moving hosts along the route.
        # nodes_file is the map file this group is ok to move on
        g = HostGroup(name, HOST_ID_DELIM)
        g.set('movementModel', 'TransitMapMovement')
        g.set('routeFile', stops_file.format(name))
        g.set('scheduleFile', schedule_file.format(name))
        g.set('routeType', 2)
        g.set('nrofHosts', NR_OF_HOSTS)
        g.set_okmap(nodes_file.format(name))
        s.add_group(g)

        # write a wkt LINESTRING with all nodes in this route
        # (includes stops and way nodes)
        writer.write_wkt_linestring(
            coords=nodes,
            file=nodes_file.format(name)
        )

        # write only stop nodes in csv with
        # 1st col: stop coords
        # 2nd col: durations between each stop to the next one
        # (will be used to generate path speeds)
        writer.write_csv_stops(
            coords=stops,
            durations=durations.get(r.name),
            file=stops_file.format(name)
        )

        # write the schedule of this line
        # (start times, start and stop ids and direction)
        writer.write_csv_schedule(
            schedule=schedule.get(r.name),
            file=schedule_file.format(name)
        )

    # add another group for all stations.
    # in this group, for each station one stationary host
    # will be created to act as a fixed relay node at the platform
    writer.write_wkt_points(stations, stations_file)
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
    # and adjust host address range to total number of hosts
    s.set('MovementModel.worldSize', '{w}, {h}'.format(
        w=math.ceil(width),
        h=math.ceil(height)
    ))
    s.set('Events1.hosts', '{min},{max}'.format(
        min=0,
        max=len(routes) * NR_OF_HOSTS + len(stations) - 1
    ))

    # write settings contents to file in ONE project root
    s.write(path.join(one_dir, '{scenario}_settings.txt'.format(
        scenario=scenario
    )))

def basename_without_ext(file: str) -> str:
    base = path.basename(file)
    base = path.splitext(base)[0]
    return str(base)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Please give .osm (and .zip input files as arguments)")
        sys.exit(1)

    main(sys.argv[1], sys.argv[2] if len(sys.argv) == 3 else None)
