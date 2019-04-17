# -*- coding: utf-8 -*-
from bs4 import BeautifulSoup
from typing import List, Tuple, Union, TextIO

class OsmRoute:
    def __init__(self,
                 name: str,
                 nodes: List[Tuple[float, float]],
                 stops: List[Tuple[float, float]]):

        self.name = name
        self.nodes = nodes
        self.stops = stops

class OsmRouteParser:
    doc = []
    node_index = {}
    way_index = {}

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

    def parse_routes(self) -> List[OsmRoute]:
        relations = self.doc.find_all('relation')
        routes = []
        routes_processed = []

        for r in relations:
            if not self.is_route(r):
                continue
            name = self.rel_name(r)
            ways = self.rel_ways(r)
            stops = self.rel_stops(r)
            if name in routes_processed or len(ways) < 2:
                continue

            print('processing route', name)

            waycoords = self.build_waycoords(ways)
            stopcoords = self.build_stopcoords(stops, waycoords)

            waycoords = self.adjust_waycoords(
                waycoords, stopcoords
            )

            if waycoords:
                routes.append(OsmRoute(name, waycoords, stopcoords))
                routes_processed.append(name)

        return routes

    def build_waycoords(self, ways):
        waycoords = []
        for i in range(len(ways) - 1):
            ref = self.ref(ways[i])
            way_nodes = self.way_nodes(ref)
            nextref = self.ref(ways[i + 1])
            nextway_nodes = self.way_nodes(nextref)

            way_nodes = self.correct_node_order(way_nodes, nextway_nodes, -1)
            waycoords = self.add_waycoords(way_nodes, waycoords)

        # last way
        lastway_nodes = self.correct_node_order(nextway_nodes, way_nodes, 0)
        waycoords = self.add_waycoords(lastway_nodes, waycoords)

        return waycoords

    def way_nodes(self, way):
        return self.way_index.get(way).select('nd')

    def add_waycoords(self, nodes, existing_coords):
        coords = []
        for n in nodes:
            c = self.node_to_coord(n)
            if c not in existing_coords:
                coords.append(c)
        return existing_coords + coords

    def build_stopcoords(self, nodes, waycoords):
        coords = []
        for n in nodes:
            c = self.node_to_coord(n)
            if c in waycoords:
                coords.append(c)
        return coords

    def node_to_coord(self, n):
        ref = self.ref(n)
        node = self.node_index.get(ref)
        return (
            float(node.attrs['lat']),
            float(node.attrs['lon'])
        )

    @staticmethod
    def adjust_waycoords(waycoords: List, stopcoords: List):
        if waycoords[0] == stopcoords[0] and \
                waycoords[-1] == stopcoords[-1]:
            return waycoords

        while waycoords[0] != stopcoords[0]:
            if waycoords[0] in stopcoords:
                print("Incoherent stop node order. Skiping route.")
                return []
            waycoords.pop(0)

        while waycoords[-1] != stopcoords[-1]:
            if waycoords[-1] in stopcoords:
                print("Incoherent stop node order. Skiping route.")
                return []
            waycoords.pop(-1)

        return waycoords


    @staticmethod
    def correct_node_order(nodes, next_nodes, index):
        if nodes[index] in next_nodes:
            return nodes

        nodes.reverse()
        print('-> reversed way')
        if nodes[index] in next_nodes:
            return nodes

        print('inconsistent way')
        return []

    @staticmethod
    def ref(tag):
        return tag.attrs['ref']

    @staticmethod
    def is_route(r):
        return r.select('tag[k="type"][v="route"]')

    @staticmethod
    def rel_name(r):
        return r.select_one('tag[k="ref"]').attrs.get('v')

    @staticmethod
    def rel_ways(r):
        return r.select('member[type="way"][role=""]')

    @staticmethod
    def rel_stops(r):
        return r.select('member[type="node"][role="stop"]')



