# -*- coding: utf-8 -*-
import sys, os, math
from os import path
from lib.osm import OsmRouteParser
from lib.project import Projector
from lib.one import HostGroup, ScenarioSettings
from lib import out

if len(sys.argv) < 2:
    print("Please give .osm input file as argument")
    sys.exit(1)

file = sys.argv[1]
scenario = path.basename(file).split('.')[0]

with open(file) as fp:
    orp = OsmRouteParser(fp)

routes = orp.parse_routes()
points = []
for r in routes:
    points.extend(r['nodes'])

proj = Projector(precision=2)
w, h = proj.init_dimensions(points)

os.chdir(path.join('..', '..'))
out_dir = path.join('data', scenario)
if not path.isdir(out_dir):
    os.mkdir(out_dir)

nodes_file = path.join(out_dir, '{}_nodes.wkt')
stops_file = path.join(out_dir, '{}_stops.csv')
stations_file = path.join(out_dir, 'stations.wkt')

s = ScenarioSettings(scenario)
stations = []

for r in routes:
    nodes = proj.transform_coords(r['nodes'])
    stops = proj.transform_coords(r['stops'])
    name = r['name']

    out.write_wkt_linestring(nodes, nodes_file.format(name))
    out.write_csv_stops(stops, stops_file.format(name))

    g = HostGroup(name+'.')
    g.set('movementModel', 'ScheduledMapRouteMovement')
    g.set('routeFile', stops_file.format(name))
    g.set('routeType', 2)
    g.set('nrofHosts', 1)
    g.set_okmap(nodes_file.format(name))
    s.add_group(g)

    stations.extend(stops)

out.write_wkt_points(stations, stations_file)
g = HostGroup('S')
g.set('movementModel', 'StationaryMultiPointMovement')
g.set('stationarySystemNr', 1)
g.set('pointFile', stations_file)
g.set('nrofHosts', len(stations))
s.add_group(g)

s.complete_groups()
s.spacer()

s.set('MovementModel.worldSize', '{w}, {h}'.format(
    w=math.ceil(w),
    h=math.ceil(h)
))
s.set('Events1.hosts', '{min},{max}'.format(
    min=0,
    max=len(stations)
))
s.write('{scn}_settings.txt'.format(
    scn=scenario
))

