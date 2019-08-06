from typing import List, Tuple

class TransitRoute:
    def __init__(self,
                 name: str,
                 nodes: List[Tuple[float, float]],
                 stops: List[Tuple[float, float]],
                 first: str = None, last: str = None):
        self.name = name
        self.first = first
        self.last = last
        self.nodes = nodes
        self.stops = stops
