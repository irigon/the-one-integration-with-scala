# -*- coding: utf-8 -*-
from bs4 import BeautifulSoup
from typing import List, Tuple, Union, TextIO

class OsmRoute:
    def __init__(self,
                 name: str, first: str, last: str,
                 nodes: List[Tuple[float, float]],
                 stops: List[Tuple[float, float]]):

        self.name = name
        self.first = first
        self.last = last
        self.nodes = nodes
        self.stops = stops

class OsmRouteParser:
    doc = []
    node_index = {}
    way_index = {}
    rel_index = {}

    def __init__(self, markup: Union[str, TextIO]):
        self.doc = BeautifulSoup(markup, 'lxml')
        self.build_indices()

    def build_indices(self):
        self.node_index = {
            node.attrs['id']: node for node in self.doc.find_all('node')
        }
        self.way_index = {
            way.attrs['id']: way for way in self.doc.find_all('way')
        }
        self.rel_index = self.build_rel_index()

    def build_rel_index(self):
        rel_index = {}
        for r in self.doc.find_all('relation'):
            if not self.is_route(r): continue
            n = self.rel_name(r)
            rel_index[n] = rel_index.get(n, []) + [r]
        return rel_index

    def parse_routes(self) -> List[OsmRoute]:
        route_names = self.rel_index.keys()
        routes = []
        routes_processed = []

        for name in route_names:
            if name in routes_processed:
                continue

            name_routes = self.rel_index[name]
            r = self.longest_route(name_routes)
            ways = self.rel_ways(r)
            stops = self.rel_stops(r)

            if not ways or not stops:
                continue

            print('processing route', name)

            first_stop = self.ref(stops[0])
            last_stop = self.ref(stops[-1])
            way_nodes = self.sort_way_nodes(ways, first_stop)

            waycoords = self.build_waycoords(way_nodes)
            stopcoords = self.build_stopcoords(stops, waycoords)

            waycoords = self.adjust_waycoords(
                waycoords, stopcoords
            )

            if len(stopcoords) < 2:
                print("- ignoring route", name, ": less than 2 stops could be parsed")
                continue

            if waycoords:
                routes.append(OsmRoute(
                    name=name,
                    first=self.stop_name(first_stop),
                    last=self.stop_name(last_stop),
                    nodes=waycoords,
                    stops=stopcoords,
                ))
                routes_processed.append(name)

        return routes

    def longest_route(self, routes):
        stop_counts = [len(self.rel_stops(r)) for r in routes]
        idx = stop_counts.index(max(stop_counts))
        return routes[idx]

    def sort_way_nodes(self, ways: List, first_stop):
        sorted_way_nodes = []
        first_way, index = self.find_first_way(ways, first_stop)
        first_way_nodes = self.way_nodes(first_way)
        if index >= len(first_way_nodes)/2:
            first_way_nodes.reverse()

        connect = first_way_nodes[-1]
        sorted_way_nodes.extend(first_way_nodes)
        ways.remove(first_way)

        while ways:
            for w in ways:
                nodes = self.way_nodes(w)
                if nodes[0] == connect or nodes[-1] == connect:
                    if nodes[-1] == connect:
                        nodes.reverse()

                    connect = nodes[-1]
                    sorted_way_nodes.extend(nodes)
                    ways.remove(w)
                    break
            else:
                break

        return sorted_way_nodes

    def find_first_way(self, ways, first_stop):
        for w in ways:
            nodes = self.way_nodes(w)
            for i, n in enumerate(nodes):
                if self.ref(n) == first_stop:
                    return w, i

    def build_waycoords(self, way_nodes):
        coords = []
        for n in way_nodes:
            c = self.node_to_coord(n)
            if c not in coords:
                coords.append(c)
        return coords

    def build_stopcoords(self, nodes, waycoords):
        coords = []
        for n in nodes:
            c = self.node_to_coord(n)
            if c in waycoords:
                coords.append(c)
        return coords

    def way_nodes(self, way):
        ref = self.ref(way)
        return self.way_index.get(ref).select('nd')

    def stop_name(self, ref):
        node = self.node_index.get(ref)
        return self.elem_name(node)

    def node_to_coord(self, n):
        ref = self.ref(n)
        node = self.node_index.get(ref)
        return (
            float(node.attrs['lat']),
            float(node.attrs['lon'])
        )

    def adjust_waycoords(self, waycoords, stopcoords):
        if not waycoords or not stopcoords:
            return []
        waycoords = self.trim_waycoords(waycoords, stopcoords, start=0)
        waycoords = self.trim_waycoords(waycoords, stopcoords, start=-1)
        return waycoords

    @staticmethod
    def trim_waycoords(waycoords, stopcoords, start):
        if not waycoords:
            return []
        while waycoords[start] != stopcoords[start]:
            if waycoords[start] in stopcoords:
                print("Incoherent stop node order. Skiping route.")
                return []
            waycoords.pop(start)
            if not waycoords:
                return []
        return waycoords

    def rel_by_name(self, name):
        return [r.parent for r in self.doc.select('relation tag[k="ref"][v="'+name+'"]')]

    @staticmethod
    def ref(tag):
        return tag.attrs['ref']

    @staticmethod
    def is_route(r):
        return r.select('tag[k="type"][v="route"]')

    @staticmethod
    def elem_name(e):
        return e.select_one('tag[k="name"]').attrs.get('v')

    @staticmethod
    def rel_name(r):
        t = r.select_one('tag[k="ref"]')
        if t:
            return t.attrs.get('v')
        return None

    @staticmethod
    def rel_ways(r):
        return r.select('member[type="way"][role=""]')

    @staticmethod
    def rel_stops(r):
        return r.select('member[type="node"][role="stop"]')



