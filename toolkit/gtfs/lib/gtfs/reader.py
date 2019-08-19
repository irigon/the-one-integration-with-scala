# -*- coding: utf-8 -*-
import zipfile, sys
import logging
from lib.gtfs.consts import *
from typing import List, Tuple, Dict
import editdistance
import pandas as pd
from pandas import DataFrame


class GTFSReader:
    """
    Reads in files from a GTFS dataset in zip format as dataframes
    to allow for fast and simple querying.
    """
    services: DataFrame
    exceptions: DataFrame
    stop_times: DataFrame
    trips: DataFrame
    stops: DataFrame
    routes: DataFrame
    shapes: DataFrame = None

    ref_trips: DataFrame
    route_types: List[int]

    def load_feed(self, gtfs_file: str, route_types: List[int], with_shapes: bool):
        """
        load (new) gtfs dataset as zip file and update internal dataframes

        :param gtfs_file: path to the zip file containing a GTFS dataset.
        :param with_shapes: require shapes or not
        :param route_types: list of route types to read from feed
        """
        try:
            with zipfile.ZipFile(gtfs_file) as z:
                to_read = GTFS_FILES
                if not with_shapes:
                    to_read.pop('shapes')
                for name, (file, required) in to_read.items():
                    with z.open(file) as fp:
                        df = pd.read_csv(fp, **PD_CSV_OPTIONS)
                        if required and df.empty:
                            raise ValueError()
                        setattr(self, name, df)
        except zipfile.BadZipFile:
            logging.error('error: '+gtfs_file+' is not a zipfile, please specify zipfile to read gtfs from')
            sys.exit(1)
        except KeyError:
            logging.error('error: '+file+' is missing in '+gtfs_file)
            if name == 'shapes':
                logging.info('Use a gtfs-osm mapper like pfaedle to import shapes or use the osm-mode directly')
            sys.exit(1)
        except ValueError:
            logging.error('error: '+file+' is empty but required.')
            if name == 'shapes':
                logging.info('Use a gtfs-osm mapper like pfaedle to import shapes or use the osm-mode directly')
            sys.exit(1)

        if with_shapes:
            self.check_shapes()
        self.route_types = route_types
        self.__merge_data()

    def check_shapes(self):
        if SHAPE_DIST not in self.stop_times or SHAPE_DIST not in self.shapes:
            logging.error("error: both stop_times.txt and shapes.txt need to have shape_dist_traveled column")
            sys.exit(1)
        if (len(self.stop_times[SHAPE_DIST].value_counts()) == 0) or \
                (len(self.shapes[SHAPE_DIST].value_counts()) == 0):
            logging.error("error: shape_dist_traveled column in stop_times.txt and shapes.txt must not be empty")
            sys.exit(1)

    def set_route_types(self, types: List[int]):
        self.route_types = types

    def trip_durations(self) -> Dict[str, List[int]]:
        """
        Get durations in minutes from one stop to another for all routes.
        If reference trips were set with set_ref_trips, only trips matching
        these will be included.

        :return: dict mapping the trip_short_name to a list of ints, representing
        the time in minutes from the previous to each stop. First value in the list is always 0.
        """

        if self.ref_trips is None:
            return {}
        r = self.ref_trips
        # convert arrival_time column to a timedelta in mins
        mins = pd.to_timedelta(r[ARR_TIME]).astype('timedelta64[m]')
        r[MINS] = mins.astype(int)
        r[MINS_PREV] = mins.shift().fillna(0).astype(int)
        # new column with the difference of minutes to the previous stop
        r[DURATION_TO] = r[MINS].sub(r[MINS_PREV])
        r = r[[ROUTE_NAME, DURATION_TO]]

        # remove first duration_to entry and replace it with 0 since the first stop
        # has no previous stop to calculate the duration from
        durations = r.groupby(ROUTE_NAME)[DURATION_TO] \
            .apply(lambda ds: [0] + list(ds)[1:])
        return dict(durations)

    def schedule(self, weekday_type: int, max_exceptions: int) -> Dict[str, List[Tuple[str, int, int]]]:
        """
        Creates a schedule for each route containing all trips in the form of
        time, start_index, end_index, direction with
            time: the start time of this trip in HH:mm:ss (24 hrs format)
            start_index: the index of the first stop in this trip
            end_index: the index of the last stop in this trip
            direction: 0 or 1 for the direction of travel

        If reference trips are set, the index refers to the stop count in the respective
        reference trip. If no reference trips are used, the longest trip (stop count) for each route
        is determined and acts as a reference to other (possibly shorter) trips.

        Filters the stop times of all routes by weekdays and a maximum allowed date exceptions
        (from calendar_dates.txt). This helps in retrieving a regular schedule
        :param weekday_type: 0 for Monday - Friday, 1 for Saturdays, 2 for Sundays
        :param max_exceptions: Number of max allowed exceptions a route service needs to respect
            to be included. This number has to be determined by trying out different tresholds
            and comparing which one delivers the best results. Generally, a number of 180 days can be
            recommended (i.e. a service has more regular days than exception days in one year).
        :return: schedule for all routes as dict with keys as route_short_name
            and values as list of [time (str), start_index (int), end_index (int), direction (int)]
        """

        services = self.__filter_weekdays(self.services, weekday_type)
        services = self.__filter_exceptions(services, max_exceptions)

        trips = pd.merge(
            self.trips,
            services[[SERVICE_ID]],
            on=SERVICE_ID,
        )
        stop_times = pd.merge(
            self.stop_times,
            trips[[TRIP_ID, SERVICE_ID]],
            on=TRIP_ID,
        )
        stop_times = pd.merge(
            stop_times,
            self.ref_trips[[ROUTE_NAME, STOP_NAME]],
            on=[ROUTE_NAME, STOP_NAME],
        ).sort_values(
            [ROUTE_NAME, TRIP_ID, STOP_SEQ]
        )

        # for each trip, keep only the first arrival_time and first and last stop name
        start_times = stop_times.groupby([TRIP_ID, ROUTE_NAME, DIRECTION_ID, SERVICE_ID]).agg({
            ARR_TIME: 'first',
            STOP_NAME: ['first', 'last']
        })
        # flatten multi-index to single column names
        start_times.columns = ['_'.join(col).strip() for col in start_times.columns.values]
        start_times.reset_index(inplace=True)

        # group ref_trips to one row per route containing all stop names in stop_nmae
        # as an inline list and merge it on start_times
        ref_list = self.ref_trips.groupby([ROUTE_NAME, DIRECTION_ID]).agg({
            STOP_NAME: list,
        }).reset_index()

        start_times = pd.merge(
            start_times,
            ref_list,
            on=ROUTE_NAME
        )

        # for first/last stop name columns, replace each value with its index in stop_name lists.
        # we have to use stop names and not ids here for creating the indices since in some
        # datasets stop names are not unique and can occur multiple times with different stop ids.
        start_times[STOP_NAME_FIRST] = start_times.apply(
            lambda row: index_stops(row, STOP_NAME_FIRST), axis=1)
        start_times[STOP_NAME_LAST] = start_times.apply(
            lambda row: index_stops(row, STOP_NAME_LAST), axis=1)

        # in some datasets hour values of arrival_time go beyond 23, eg 24:05:00.
        # this applies a modulo(24) transform to hours in each row of arrival_time_first.
        start_times[ARR_TIME_FIRST] = start_times.apply(
            lambda row: mod_hours(row), axis=1)

        start_times = start_times[
            [ROUTE_NAME,
             ARR_TIME_FIRST,
             STOP_NAME_FIRST,
             STOP_NAME_LAST]
        ].sort_values(
            [ROUTE_NAME, ARR_TIME_FIRST]
        )

        # build output dict
        out = {r: [] for r in start_times[ROUTE_NAME].unique()}
        for r in start_times.itertuples():
            if r.stop_name_first != r.stop_name_last:
                out[r[1]].append(tuple(r[2:]))
        return out

    def build_ref_trips(self):
        """
        To align gtfs data with external route information, a list of
        reference routes is given, containing the route name, stops on this route
        and the first/last stop name. If these values align with a trip of the gtfs
        dataset, the trips will be identified and stored in ref_trips to represent the
        given route. This way the dataset can be filtered to include only specific
        routes in the schedule and trip_durations.

        To be more tolerant with external data the first/last stop names don't neccesarily
        need to be the exact names of the stops in the dataset. Instead a match for text similarity
        (edit distance) is made and the best ranking combination of first/last stop will be used.

        :param ref_routes: List of Tuples of route name (str),
        first stop name (str), last stop name (str), stop size (int)
        """

        if ROUTE_NAME not in self.stop_times:
            self.__merge_data()

        # group stop times by trips, counting stops of each
        # trip and keeping the first and last stop name
        trip_stop_counts = self.stop_times.groupby(
            [TRIP_ID, ROUTE_NAME, DIRECTION_ID]
        ).agg({
            ARR_TIME: 'size',
            STOP_NAME: ['first', 'last']
        })

        # some semantic sugar: rename column used for keeping stop count
        # and flatten multi-index to single column names
        # (STOPS, 'size') becomes STOPS_SIZE
        # (STOP_NAME, 'first') becomes STOP_NAME_FIRST etc.
        trip_stop_counts.rename(
            columns={ARR_TIME: STOPS},
            inplace=True
        )
        trip_stop_counts.columns = [
            '_'.join(col).strip() for col in trip_stop_counts.columns.values
        ]
        trip_stop_counts.reset_index(inplace=True)

        max_trips = trip_stop_counts.sort_values(
            [ROUTE_NAME, STOPS_SIZE],
        )
        max_trips.drop_duplicates(
            ROUTE_NAME,
            keep='last',
            inplace=True,
        )
        ref_trips = pd.merge(
            max_trips[TRIP_ID],
            self.stop_times,
            on=TRIP_ID
        )
        ref_trips = pd.merge(
            ref_trips,
            self.trips[[TRIP_ID, SHAPE_ID]],
            on=TRIP_ID
        )

        self.ref_trips = ref_trips

    def set_ref_trips(self, ref_routes: List[Tuple[str, str, str, int]]):
        """
        To align gtfs data with external route information, a list of
        reference routes is given, containing the route name, stops on this route
        and the first/last stop name. If these values align with a trip of the gtfs
        dataset, the trips will be identified and stored in ref_trips to represent the
        given route. This way the dataset can be filtered to include only specific
        routes in the schedule and trip_durations.

        To be more tolerant with external data the first/last stop names don't neccesarily
        need to be the exact names of the stops in the dataset. Instead a match for text similarity
        (edit distance) is made and the best ranking combination of first/last stop will be used.

        :param ref_routes: List of Tuples of route name (str),
        first stop name (str), last stop name (str), stop size (int)
        """

        if ROUTE_NAME not in self.stop_times:
            self.__merge_data()

        # create dataframe from given routes
        ref_routes = pd.DataFrame(
            ref_routes,
            columns=[ROUTE_NAME, REF_NAME_FIRST, REF_NAME_LAST, STOPS_SIZE]
        )
        # group stop times by trips, counting stops of each
        # trip and keeping the first and last stop name
        trip_stop_counts = self.stop_times.groupby(
            [TRIP_ID, ROUTE_NAME, DIRECTION_ID]
        ).agg({
            ARR_TIME: 'size',
            STOP_NAME: ['first', 'last']
        })

        # some semantic sugar: rename column used for keeping stop count
        # and flatten multi-index to single column names
        # (STOPS, 'size') becomes STOPS_SIZE
        # (STOP_NAME, 'first') becomes STOP_NAME_FIRST etc.
        trip_stop_counts.rename(
            columns={ARR_TIME: STOPS},
            inplace=True
        )
        trip_stop_counts.columns = [
            '_'.join(col).strip() for col in trip_stop_counts.columns.values
        ]
        trip_stop_counts.reset_index(inplace=True)

        # keep only one trip per route with the same stop size
        # and first/last stop name (these trips are considered to be equal)
        trip_stop_counts.drop_duplicates(
            [STOPS_SIZE, STOP_NAME_FIRST, STOP_NAME_LAST],
            keep='first',
            inplace=True
        )

        # eventually, merge the single trips (containing stop counts and
        # first/last names) with the reference routes and their respective first/last names
        # ROUTE_NAME and STOPS_SIZE is expected to be equal, otherwise no ref_trips item
        # for this route will be created
        ref_trips = pd.merge(
            trip_stop_counts,
            ref_routes,
            on=[ROUTE_NAME]
        )

        # filter trips on those either matching the stop count of the respective reference route
        # or by the maximum stop count if no match was found. This way the trip can still be
        # used, but has to be cropped to the respective reference route stop count
        ref_trips = ref_trips[
            (ref_trips[STOPS_SIZE_X] == ref_trips[STOPS_SIZE_Y]) |
            (ref_trips[STOPS_SIZE_X] == ref_trips.groupby([ROUTE_NAME])[STOPS_SIZE_X].transform(max))
            ]

        # to match the reference route to one trip, first/last stop names
        # have to be compared. These are not neccesarily expected to be equal, but similar.
        # for similarity measurement, the levenshtein editdistance between stop name and
        # ref stop name strings is evaluated and averaged between stops (SCORE column)
        # finally, the trip with the best score for each route is kept as reference trip
        ref_trips[SCORE] = ref_trips.apply(score, axis=1)
        ref_trips.sort_values([ROUTE_NAME, SCORE], inplace=True)
        ref_trips.drop_duplicates(ROUTE_NAME, keep='last', inplace=True)
        ref_trips = pd.merge(
            ref_trips[[TRIP_ID, STOPS_SIZE_Y]],
            self.stop_times,
            on=TRIP_ID
        )

        # rebuild stop sequence column to have a consistent enumeration.
        # (we don't want to rely on the given stop_sequence here,
        # in some cases the enumberation is 0-based, in some 1-based.
        ref_trips[STOP_SEQ] = ref_trips.groupby(ROUTE_NAME).cumcount()

        # clip ref_trips to given stops size of reference trips
        # (cut off stops at the end that exceed required stops size)
        ref_trips = ref_trips[ref_trips[STOP_SEQ] < ref_trips[STOPS_SIZE_Y]]
        del ref_trips[STOPS_SIZE_Y]
        self.ref_trips = ref_trips

    # filter by days, 0: weekdays, 1: saturdays, 2: sundays
    @staticmethod
    def __filter_weekdays(services: DataFrame, weekday_type: int) -> DataFrame:
        s = services
        return s.loc[
            (
                    (weekday_type == 0) &
                    (s[MON] == 1) &
                    (s[TUE] == 1) &
                    (s[WED] == 1) &
                    (s[THU] == 1) &
                    (s[FRI] == 1)
            ) |
            (  # When there's not a service date that covers all work days
               # fall back to just using monday
                    (weekday_type == 0) &
                    (s[MON] == 1)
            ) |
            (
                    (weekday_type == 1) &
                    (s[SAT] == 1)
            ) |
            (
                    (weekday_type == 2) &
                    (s[SUN] == 1)
            )
            ]

    # filter by exeption count
    def __filter_exceptions(self, services: DataFrame, days_treshold: int) -> DataFrame:
        s = services
        if self.exceptions.empty:
            return s
        counts = self.exceptions.groupby(SERVICE_ID) \
            .count() \
            .reset_index()
        s = pd.merge(s, counts, on=SERVICE_ID, how='left')
        s.fillna(0, inplace=True)
        s = s.loc[(s[DATE] < days_treshold)]
        del s[TYPE]
        return s

    # merge all needed columns into the stop_times dataframe
    def __merge_data(self):
        self.routes = self.routes[self.routes[ROUTE_TYPE].isin(self.route_types)]
        # define needed trip fields, add shape_id if shapes are present
        trip_fields = [ROUTE_ID, SERVICE_ID, TRIP_ID, DIRECTION_ID]
        if self.shapes is not None:
            trip_fields.append(SHAPE_ID)
        # merge trips on routes
        self.trips = pd.merge(
            self.trips[trip_fields],
            self.routes[[ROUTE_ID, ROUTE_NAME]],
            on=ROUTE_ID
        )
        # merge trips on services
        self.trips = pd.merge(
            self.trips,
            self.services[SERVICE_ID],
            on=SERVICE_ID
        )
        # define needed stop_times fields, add shape_id if shapes are present
        stop_times_fields = [TRIP_ID, ARR_TIME, STOP_ID, STOP_SEQ]
        if self.shapes is not None:
            stop_times_fields.append(SHAPE_DIST)
        # merge trips on stop_times
        self.stop_times = pd.merge(
            self.trips[[TRIP_ID, ROUTE_NAME, DIRECTION_ID]],
            self.stop_times[stop_times_fields],
            on=TRIP_ID
        )
        # merge stop_times on stops and sort by trips and stop sequence
        self.stop_times = pd.merge(
            self.stop_times,
            self.stops[[STOP_ID, STOP_NAME]],
            on=STOP_ID
        )
        self.stop_times.sort_values(
            [TRIP_ID, STOP_SEQ],
            inplace=True
        )

    def route_names(self):
        routes = self.ref_trips.drop_duplicates(ROUTE_NAME, keep='first')
        routes = routes[ROUTE_NAME]
        return list(routes)

    def shape_paths(self):
        ref_trips = self.ref_trips.drop_duplicates(TRIP_ID, keep='first')
        ref_shapes = pd.merge(
            self.shapes,
            ref_trips[[ROUTE_NAME, SHAPE_ID]],
            on=SHAPE_ID
        )
        ref_shapes.sort_values(
            [ROUTE_NAME, SHAPE_SEQ],
            inplace=True
        )
        ref_shapes = ref_shapes[[ROUTE_NAME, SHAPE_LAT, SHAPE_LON]]
        out = {r: [] for r in ref_shapes[ROUTE_NAME].unique()}
        for r in ref_shapes.itertuples():
            out[r[1]].append((r[2], r[3]))
        return out

    def shape_stops(self):
        stop_shapes = pd.merge(
            self.ref_trips[[ROUTE_NAME, STOP_SEQ, SHAPE_ID, SHAPE_DIST]],
            self.shapes,
            on=[SHAPE_ID, SHAPE_DIST]
        )
        stop_shapes.sort_values([
            ROUTE_NAME, STOP_SEQ],
            inplace=True
        )

        stop_shapes = stop_shapes[[ROUTE_NAME, SHAPE_LAT, SHAPE_LON]]
        out = {r: [] for r in stop_shapes[ROUTE_NAME].unique()}
        for r in stop_shapes.itertuples():
            out[r[1]].append((r[2], r[3]))
        return out


def index_stops(row, col):
    i = row[STOP_NAME].index(row[col])
    return i


def mod_hours(row):
    t = row[ARR_TIME_FIRST]
    h = int(t.split(':')[0])
    if h > 23:
        t = ':'.join(
            [str(h % 24).zfill(2)] +
            t.split(':')[1:]
        )
    return t


def distance(s1, s2):
    maxlen = max(len(s1), len(s2))
    dist = editdistance.eval(s1, s2)
    return (maxlen - dist) / maxlen


def score(row):
    d_first = distance(row[STOP_NAME_FIRST], row[REF_NAME_FIRST])
    d_last = distance(row[STOP_NAME_LAST], row[REF_NAME_LAST])
    return (d_first + d_last) / 2
