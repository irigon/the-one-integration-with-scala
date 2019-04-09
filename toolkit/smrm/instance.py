# -*- coding: utf-8 -*-
import sys
from lib.osm_routes import OsmRouteParser
from lib.project import Projector
from lib.wkt import write_wkt

with open(sys.argv[1]) as fp:
    orp = OsmRouteParser(fp)

proj = Projector(precision=2)
routes = orp.parse_routes()
points = []
for r in routes:
    points.extend(r['nodes'])
    points.extend(r['stops'])

w, h = proj.init_dimensions(points)

for r in routes:
    r['nodes'] = proj.transform_coords(r['nodes'])
    r['stops'] = proj.transform_coords(r['stops'])
    write_wkt(r['nodes'], str(r['name']) + '_nodes.wkt')
    write_wkt(r['stops'], str(r['name']) + '_stops.wkt')
