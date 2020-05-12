from typing import List, Tuple

class TransitRoute:
    def __init__(self,
                 name: str,
                 nodes: List[Tuple[float, float]],
                 stops: List[Tuple[float, float]],
                 ):
        self.name = name
        self.nodes = nodes
        self.stops = stops
