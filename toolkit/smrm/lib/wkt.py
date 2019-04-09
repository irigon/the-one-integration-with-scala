# -*- coding: utf-8 -*-
from typing import List, Tuple
WKT_BEGIN = 'LINESTRING ('
WKT_END = ')'
WKT_CRD_SEP = ' '
WKT_PNT_SEP = ', '

def write_wkt(coords: List[Tuple[float, float]], file: str, append=False):
    content = ''
    if append:
        content += '\n\n'
    content += WKT_BEGIN

    for i, c in enumerate(coords):
        content += str(c[0]) + WKT_CRD_SEP + str(c[1])
        if i < len(coords) - 1:
            content += WKT_PNT_SEP

    content += WKT_END

    with open(file, 'a+' if append else 'w+') as fp:
        fp.write(content)


