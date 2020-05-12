# -*- coding: utf-8 -*-
import zipfile, sys
import logging

from lib.commons import TransitRoute
from lib.gtfs.consts import *
from typing import List, Tuple, Dict
import editdistance
import pandas as pd
from pandas import DataFrame

from datetime import datetime
from datetime import timedelta


class GTFSReader:

    services: DataFrame
    exceptions: DataFrame
    stop_times: DataFrame
    trips: DataFrame
    stops: DataFrame
    routes: DataFrame
    shapes: DataFrame = None

    ref_trips: DataFrame
    route_types: []

    def load_feed(self, gtfs_file: str, with_shapes: bool):
        try:
            with zipfile.ZipFile(gtfs_file) as z:
                to_read = GTFS_FILES

                for name, (file, required) in to_read.items():
                    with z.open(file) as fp:
                        df = pd.read_csv(fp, **PD_CSV_OPTIONS)
                        if required and df.empty:
                            raise ValueError()
                        setattr(self, name, df)
        except zipfile.BadZipFile:
            logging.error('error: ' + gtfs_file + ' is not a zipfile, please specify zipfile to read gtfs from')
            sys.exit(1)
        except KeyError:
            logging.error('error: ' + file + ' is missing in ' + gtfs_file)
            if name == 'shapes':
                logging.info('Use a gtfs-osm mapper like pfaedle to import shapes or use the osm-mode directly')
            sys.exit(1)
        except ValueError:
            logging.error('error: ' + file + ' is empty but required.')
            if name == 'shapes':
                logging.info('Use a gtfs-osm mapper like pfaedle to import shapes or use the osm-mode directly')
            sys.exit(1)

    def apply_exceptions(self, chosen_day, trips):
        chosen_day_str = chosen_day.strftime("%Y%m%d")
        weekday = chosen_day.weekday()

        # filter exceptions for this day
        exceptions = self.exceptions[self.exceptions['date'] == int(chosen_day_str)]
        exception_not_work_this_day = exceptions[exceptions['exception_type'] == 2.0]
        exceptionaly_work_this_day = exceptions[exceptions['exception_type'] == 1.0]

        # get all services for this week(end) day
        day_name = chosen_day.strftime("%A").lower()
        services_of_the_day = self.services[self.services[day_name] == 1.0][['service_id', day_name, 'start_date', 'end_date']]

        # exclude services that have a start_date after or end_date before the chosen_date
        services_of_the_day = services_of_the_day[services_of_the_day['start_date'] <= float(chosen_day_str)]
        services_of_the_day = services_of_the_day[services_of_the_day['end_date'] >= float(chosen_day_str)]

        # add trips that exceptionally happen on this day
        all_services = pd.concat([services_of_the_day, exceptionaly_work_this_day], ignore_index=True, sort=False)
        all_services = all_services[~all_services['service_id'].isin(exception_not_work_this_day['service_id'])]
        trips_of_interest = trips[trips['service_id'].isin(all_services['service_id'])]
        return trips_of_interest

    # 1. Get all routes of "type" 0.0 --> Tram
    # 2. Apply exceptions
    #   2.1 Exclude services from this day that exceptionally this day should not work
    #   2.2 Add services that work exceptionally on the chosen day
    # 3. Add stop_times
    def set_trips_of_interest(self, type: float, day):
        # filter out routes of interest
        routes = self.routes[self.routes.route_type == float(type)]

        # merge routes of interest and their trips
        self.trips_of_interest = pd.merge(routes, self.trips, on='route_id')

        # exclude uninteresting fields
        self.trips_of_interest = self.trips_of_interest.loc[:,
                            ['route_id', 'route_short_name', 'route_type', 'service_id', 'trip_id', 'direction_id', 'shape_id']]

        # define a day between start and end --> TODO: use a selected day instead of choosing a random one

        self.trips_of_interest = self.apply_exceptions(day, self.trips_of_interest)



        # retain just the required information:
        trips_of_interest = self.trips_of_interest[['trip_id', 'route_short_name', 'direction_id', 'shape_id']]

    def assert_attributes(self, att_list):
        for att in att_list:
            if not hasattr(self, att):
                logging.error("{} not set".format(att))
                exit(1)


    def build_ref_trips (self):
        self.assert_attributes(['trips_of_interest', 'stop_times'])

        trip_stop_counts = self.stop_times.groupby(
            [TRIP_ID, ROUTE_NAME, DIRECTION_ID]
        ).agg({
            ARR_TIME: 'size',
            STOP_NAME: ['first', 'last', list]
        })
        trip_stop_counts.rename(
            columns={ARR_TIME: STOPS},
            inplace=True
        )
        trip_stop_counts.columns = [
            '_'.join(col).strip() for col in trip_stop_counts.columns.values
        ]
        trip_stop_counts.reset_index(inplace=True)

        # Ref trips should be the path that the stations of the majority of the trips
        # Therefore, we search for the stop list that contains the greatest number of start/end from all trips for a line

        # Add a line that tells how many trips are contained in this path
        grouped = trip_stop_counts.groupby([ROUTE_NAME])
        trip_stop_counts['trips_matched'] = grouped.apply(lambda x: ((x.stop_name_list.apply(x.stop_name_first.isin) &
                                                        (x.stop_name_list.apply(x.stop_name_last.isin))))).transform('sum', axis=1)

        max_trips = trip_stop_counts.sort_values(
            [ROUTE_NAME, 'trips_matched'],
        )
        # take the longest trip
        max_trips.drop_duplicates(
            ROUTE_NAME,
            keep='last',
            inplace=True,
        )

        # Exclude trips different from the reference
        # The reference will be used to set index
        ref_trips = pd.merge(
            max_trips[TRIP_ID],
            self.stop_times,
            on=TRIP_ID
        )
        ref_trips = pd.merge(
            ref_trips,
            self.trips_of_interest[[TRIP_ID, SHAPE_ID]],
            on=TRIP_ID
        )

        self.ref_trips = ref_trips


    def set_stop_times(self):
        self.assert_attributes(['trips_of_interest'])

        # add the stop_time information for this trips
        stop_times_fields = [TRIP_ID, ARR_TIME, STOP_ID, STOP_SEQ, SHAPE_DIST]
        stop_times = pd.merge(self.trips_of_interest[['trip_id', 'route_short_name', 'direction_id']],
                              self.stop_times[stop_times_fields], on='trip_id')

        stop_times.sort_values('arrival_time', inplace=True)
        # merge stop_times on stops and sort by trips and stop sequence
        stop_times = pd.merge(
            stop_times,
            self.stops[[STOP_ID, STOP_NAME]],
            on=STOP_ID
        )

        stop_times.sort_values(
            [TRIP_ID, STOP_SEQ],
            inplace=True
        )

        self.stop_times = stop_times

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

    def shape_routes(self):
        route_names = self.route_names()
        paths = self.shape_paths()
        stops = self.shape_stops()

        routes = []
        for r in route_names:
            routes.append(
                TransitRoute(
                    name=r,
                    nodes=paths[r],
                    stops=stops[r]
                )
            )
        return routes

    def get_index(self, row, col):
        try:
            idx = row[STOP_NAME].index(row[col])
        except ValueError:
            idx = -1
        return idx

    def mod_hours(self,row):
        t = row[ARR_TIME_FIRST]
        h = int(t.split(':')[0])
        if h > 23:
            t = ':'.join(
                [str(h % 24).zfill(2)] +
                t.split(':')[1:]
            )
        return t

    def get_first_day_from_feed(self, day_name):
        start_day_str = self.services.start_date.head(1).iloc[0]
        end_day_str = self.services.end_date.head(1).iloc[0]

        start_day = datetime.strptime(str(start_day_str), '%Y%m%d')
        end_day = datetime.strptime(str(end_day_str), '%Y%m%d')

        day = start_day
        while day != end_day:
            if day.strftime("%A") == day_name:
                return day
            day = day + timedelta(days=1)
        if day.strftime("%A") == day_name:
            return day
        return None

    def print_feed_info(self):
        start_day_str = self.services.start_date.sort_values().iloc[0]
        end_day_str = self.services.end_date.sort_values().iloc[-1]
        #end_day_str = self.services.end_date.head(1).iloc[0]
        print("This feed defines transit from ")


    def schedule(self):
        self.assert_attributes(['stop_times'])

        stop_times = self.stop_times.sort_values([ROUTE_NAME, TRIP_ID, STOP_SEQ])

        start_times = stop_times.groupby([TRIP_ID, ROUTE_NAME, DIRECTION_ID]).agg({
            ARR_TIME: 'first',
            STOP_NAME: ['first', 'last']
        })
        # flatten multi-index to single column names
        start_times.columns = ['_'.join(col).strip() for col in start_times.columns.values]
        start_times.reset_index(inplace=True)

        ref_list = self.ref_trips.groupby([ROUTE_NAME, DIRECTION_ID]).agg({
            STOP_NAME: list,
        }).reset_index()

        start_times = pd.merge(
            start_times,
            ref_list,
            on=[ROUTE_NAME]
        )

        # for first/last stop name columns, replace each value with its index in stop_name lists.
        # we have to use stop names and not ids here for creating the indices since in some
        # datasets stop names are not unique and can occur multiple times with different stop ids.
        start_times[STOP_NAME_FIRST + '_idx'] = start_times.apply(
            lambda row: self.get_index(row, STOP_NAME_FIRST), axis=1)
        start_times[STOP_NAME_LAST + '_idx'] = start_times.apply(
            lambda row: self.get_index(row, STOP_NAME_LAST), axis=1)

        # in some datasets hour values of arrival_time go beyond 23, eg 24:05:00.
        # this applies a modulo(24) transform to hours in each row of arrival_time_first.
        start_times[ARR_TIME_FIRST] = start_times.apply(
            lambda row: self.mod_hours(row), axis=1)

        start_times = start_times[
            [ROUTE_NAME,
             ARR_TIME_FIRST,
             STOP_NAME_FIRST + '_idx',
             STOP_NAME_LAST + '_idx']
        ].sort_values(
            [ROUTE_NAME, ARR_TIME_FIRST]
        )

        # build output dict
        schedule = {r: [] for r in start_times[ROUTE_NAME].unique()}
        start_s, end_s, same_s = 0,0,0
        for r in start_times.itertuples():
            if int(r.stop_name_first_idx) == int(r.stop_name_last_idx):
                #logging.warning('First and last stop are the same, ignoring: {}'.format(r.stop_name_last_idx))
                same_s += 1
            elif int(r.stop_name_first_idx) < 0.0:
                #logging.warning('Start station not found, ignoring: {}'.format(r.stop_name_first_idx))
                start_s += 1
            elif int(r.stop_name_last_idx) < 0.0:
                #logging.warning('End station not found, ignoring: {}'.format(r.stop_name_last_idx))
                end_s += 1
            else:
                schedule[r.route_short_name].append(tuple((r.arrival_time_first, r.stop_name_first_idx, r.stop_name_last_idx)))
        print('{} trips ignored: start/end/same : {}/{}/{}'.format(
            start_s + end_s + same_s, start_s, end_s, same_s)
        )
        return schedule


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
