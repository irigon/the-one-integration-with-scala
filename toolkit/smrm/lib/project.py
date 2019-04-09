# -*- coding: utf-8 -*-
from geopy import distance
from typing import List, Tuple
class Projector:

    lat_min = 90
    lat_max = -90
    lon_min = 180
    lon_max = -180

    def __init__(self, precision: int):
        self.precision = precision

    def init_dimensions(self, coords: List[Tuple[float, float]]) -> (float, float):
        for c in coords:
            if c[0] < self.lat_min:  self.lat_min = c[0]
            if c[0] > self.lat_max:  self.lat_max = c[0]
            if c[1] < self.lon_min:  self.lon_min = c[1]
            if c[1] > self.lon_max:  self.lon_max = c[1]

        width = distance.distance(
            (self.lat_min, self.lon_min),
            (self.lat_min, self.lon_max)
        )
        height = distance.distance(
            (self.lat_min, self.lon_min),
            (self.lat_max, self.lon_min)
        )
        return width.m, height.m


    def transform_coords(self, coords: List[Tuple[float, float]]) -> List[Tuple[float, float]]:
        new_coords = []
        for c in coords:
            new_coords.append((
                round(distance.distance(
                    (c[0], c[1]), (c[0], self.lon_min)
                ).m, self.precision),
                round(distance.distance(
                    (c[0], c[1]), (self.lat_min, c[1])
                ).m, self.precision)
            ))
        return new_coords

