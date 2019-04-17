# -*- coding: utf-8 -*-
from typing import List, Tuple
import csv
WKT_BEGIN_LNSTR = 'LINESTRING ('
WKT_BEGIN_POINT = 'POINT ('
WKT_END = ')'
WKT_CRD_SEP = ' '
WKT_PNT_SEP = ', '
CSV_DELIMITER = ','
CSV_QUOTECHAR = '"'

def write_wkt_linestring(coords: List[Tuple[float, float]], file: str, append=False) -> None:
    content = ''
    if append:
        content += '\n\n'
    content += WKT_BEGIN_LNSTR

    for i, c in enumerate(coords):
        content += str(c[0]) + WKT_CRD_SEP + str(c[1])
        if i < len(coords) - 1:
            content += WKT_PNT_SEP

    content += WKT_END

    with open(file, 'a+' if append else 'w+') as fp:
        fp.write(content)

def write_wkt_points(coords: List[Tuple[float, float]], file: str, append=False):
    content = ''
    if append:
        content += '\n\n'

    for i, c in enumerate(coords):
        content += WKT_BEGIN_POINT
        content += str(c[0]) + WKT_CRD_SEP + str(c[1])
        content += WKT_END
        if i < len(coords) - 1:
            content += '\n\n'

    with open(file, 'a+' if append else 'w+') as fp:
        fp.write(content)


def write_csv_stops(stops: List[Tuple[float, float]], file: str):
    with open(file, 'w+') as fp:
        w = csv.writer(fp,
                       delimiter=CSV_DELIMITER,
                       quotechar=CSV_QUOTECHAR,
                       quoting=csv.QUOTE_MINIMAL)
        for s in stops:
            w.writerow([
                '{} {}'.format(s[0], s[1]),
                5  # min
            ])
