# -*- coding: utf-8 -*-
import sys, os, math
import datetime
import time
import argparse
import logging
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
HOST_ID_DELIM = '_'
MIN_SWITCH_TIME = 60  # seconds

def osm_routes(gtfs: GTFSReader, osm_file) -> List[TransitRoute]:
    # reads all routes (relations with tag[k="type"][v="route"])
    # from osm file

    logging.info("reading route paths from osm file")
    with open(osm_file) as fp:
        orp = OsmRouteParser(fp)
    routes = orp.parse_routes()
    ref_routes = [(r.name, r.first, r.last, len(r.stops)) for r in routes]

    gtfs.set_ref_trips(ref_routes)
    return routes

def shape_routes(gtfs: GTFSReader) -> List[TransitRoute]:
    # reads route paths from gtfs shapes

    logging.info("reading route paths from gtfs shapes")
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

def main(args):
    scenario = args.name or basename_without_ext(args.gtfs_file)
    route_types = args.types.split(',')
    with_shapes = not args.osm

    logging.info('reading gtfs feed in '+args.gtfs_file)
    gtfs = GTFSReader()
    gtfs.load_feed(args.gtfs_file, route_types=route_types, with_shapes=with_shapes)

    if args.osm:
        routes = osm_routes(gtfs, args.osm)
    else:
        routes = shape_routes(gtfs)

    logging.info("building schedule and trip durations")
    schedule = gtfs.schedule(weekday_type=args.weekday, max_exceptions=args.max_exceptions)
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

    logging.info('creating ONE scenario "'+scenario+'" and writing files')

    nodes_file = path.join(out_dir, NODES_FILE)
    stops_file = path.join(out_dir, STOPS_FILE)
    schedule_file = path.join(out_dir, SCHEDULE_FILE)
    stations_file = path.join(out_dir, STATIONS_FILE)

    # begin contents of a ONE settings file for this scenario
    s = ScenarioSettings(scenario)
    stations = set()

    nhosts = None
    min_hosts = None
    if args.nhosts.isdigit():
        nhosts = int(args.nhosts)
    elif args.nhosts == 'auto':
        min_hosts = determine_min_hosts(schedule, durations)
    else:
        logging.error('invalid value of parameter nhosts. must be either positive int or "auto"')
        exit(1)

    hosts_total = 0
    for r in routes:
        name = r.name
        if not schedule.get(name) or durations.get(name):
            continue

        # transform the coordinates from lat,long
        # to x,y tuples on projection pane
        nodes = proj.transform_coords(r.nodes)
        stops = proj.transform_coords(r.stops)

        hosts = nhosts if nhosts is not None else min_hosts.get(name)
        hosts_total += hosts
        stations.update(stops)

        # for each route, create a host group.
        # this group will contain the moving hosts along the route.
        # nodes_file is the map file this group is ok to move on
        g = HostGroup(name, HOST_ID_DELIM)
        g.set('movementModel', 'TransitMapMovement')
        g.set('routeFile', stops_file.format(name))
        g.set('scheduleFile', schedule_file.format(name))
        g.set('routeType', 2)
        g.set('nrofHosts', hosts)
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
        max=hosts_total + len(stations) - 1
    ))

    # write settings contents to file in ONE project root
    s.write(path.join(one_dir, '{scenario}_settings.txt'.format(
        scenario=scenario
    )))

def basename_without_ext(file: str) -> str:
    base = path.basename(file)
    base = path.splitext(base)[0]
    return str(base)

def determine_min_hosts(schedule, durations):
    min_hosts = {}
    for route, trips in schedule.items():
        durs = durations.get(route)

        # convert start time of each trip from time to seconds
        trips = trips.copy()
        for i, trip in enumerate(trips):
            t_start, h_start, h_end = trip
            t_seconds = time_to_seconds(t_start)
            trips[i] = t_seconds, h_start, h_end

        visited = []
        hosts = 0

        for i, trip in enumerate(trips):
            if i in visited:
                continue
            hosts += 1
            t_end, h_end, _ = trip
            t_end -= MIN_SWITCH_TIME

            for j, next_trip in enumerate(trips[i:]):
                if i+j in visited:
                    continue
                t_start_next, h_start_next, h_end_next = next_trip

                if t_start_next >= t_end + MIN_SWITCH_TIME \
                        and h_start_next == h_end:
                    visited.append(i+j)
                    durs_start = min(h_start_next, h_end_next)
                    durs_end = max(h_start_next, h_end_next) + 1
                    next_trip_dur = sum(durs[durs_start:durs_end]) * 60
                    t_end = t_start_next + next_trip_dur
                    h_end = h_end_next

        min_hosts[route] = hosts
    return min_hosts

def time_to_seconds(time_str):
    t = time.strptime(time_str,'%H:%M:%S')
    secs = datetime.timedelta(hours=t.tm_hour,minutes=t.tm_min,seconds=t.tm_sec).total_seconds()
    return int(secs)


if __name__ == '__main__':
    logging.basicConfig(
        format='%(asctime)s - %(message)s',
        datefmt='%d-%b-%y %H:%M:%S',
        level=logging.INFO
    )
    parser = argparse.ArgumentParser(description='Creates a ONE scenario from a GTFS feed. If the feed ' +
                                     'does not provide shape data, the routes can also be matched to and read from ' +
                                     'an OpenStreetMap file (--osm).')
    parser.add_argument('gtfs_file', type=str,
                        help='the GTFS feed to parse (.zip file).')
    parser.add_argument('--osm', type=str,
                        help='the .osm file to match routes with if no shapes are present in the gtfs feed.')
    parser.add_argument('--types', '-t', default='0', type=str,
                        help='limits the the route types to parse from the gtfs feed, comma separated. ' +
                        'See https://developers.google.com/transit/gtfs/reference/#routestxt for route type ' +
                        'definitions. Defaults to 0 (tram routes)')
    parser.add_argument('--weekday', '-d', default=0, type=int,
                        help='limits the weekdays to parse trip for from the gtfs feed.' +
                        'Options: 0 - only working days (mo-fri), 1 - only saturdays, 2 - only sundays. ' +
                        'Defaults to 0')
    parser.add_argument('--max_exceptions', '-e', default=180, type=int,
                        help='limits the days to parse trips for to service dates with a maximum number of exceptions.' +
                        'This way irregular service times with a lot of exceptions can be filtered out. ' +
                        'Defaults to 180 (more than half the year needs to be regular)')
    parser.add_argument('--nhosts', '-n', default='5', type=str,
                        help='sets the number of hosts that will be created in each host group.' +
                        'Use "auto" to determine the minimum number of hosts necessary to respect the whole schedule ' +
                        'correctly (all trips are carried out). Use this with caution as it can yield to a large ' +
                        'number of hosts in your scenario. Defaults to 5.')
    parser.add_argument('--name', '-o', default='', type=str,
                        help='sets the name of this scenario. Will also be used for a sub-directory containing all ' +
                        'needed files and for the file name of the settings config. Defaults to the gtfs-feed filename')

    args = parser.parse_args()
    main(args)
