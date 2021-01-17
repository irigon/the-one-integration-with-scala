
# argument = schedule file

from optparse import OptionParser
import datetime
from datetime import timedelta


parser = OptionParser()
parser.add_option("-t", "--schedule-file", dest="schedule_file", type="str",
                  help="Schedule file .csv")
parser.add_option("-s", "--haltstelle", dest = "haltestelle_file", type = "str",
                  help="Stop file .csv")
(options, args) = parser.parse_args()

schedule_fname = options.schedule_file
stop_fname = options.haltestelle_file

with open(schedule_fname, 'r') as fd:
    trips = fd.readlines()
with open(stop_fname, 'r') as fd:
    stations_info = fd.readlines()

def calculate_trip_duration(depart_idx, end_idx, stations_info):
    if depart_idx < end_idx:
        idx1, idx2 = depart_idx, end_idx
    else:
        idx1, idx2 = end_idx, depart_idx

    partial_list = stations_info[idx1:idx2+1]
    required_time = lambda x : int(x.split(',')[1])
    time_list = [required_time(t) for t in partial_list]
    return sum(time_list)

# Inputr - trip as string, output - trip as dict formated:
#   {datetime departure, last_station_idx, duration in seconds}
def format_trip(trip_line):
    arrival_str, depart_idx, end_idx = trip_line.split(",")
    arrival_date = datetime.datetime.strptime(arrival_str, "%d:%m:%H:%M:%S")
    duration = calculate_trip_duration(int(depart_idx), int(end_idx), stations_info)
    return {"arrival":arrival_date, "ini_station": int(depart_idx), "last_station":int(end_idx), "duration":duration}


# Returns the next trip a vehicle service trip_dict could serve, i.e.,
# Returns the first trip from trip_dict.dest after trip_dict depart + trip_time
def find_next_possible_trip(trip_dict, trip_list):
    next_trip = {'trip': None, 'wait_seconds': float("inf")}
    arrival = trip_dict['arrival'] + timedelta(seconds=trip_dict['duration'])
    for trip in trip_list:
        # A candidate must depart from the same station, have a departure time > than the arrival
        trip_d = format_trip(trip)
        if trip_dict['last_station'] == trip_d['ini_station'] and arrival < trip_d['arrival']:
            diff_seconds = (trip_d['arrival'] - arrival).total_seconds()
            if diff_seconds < next_trip['wait_seconds']:
                next_trip = {'trip': trip, 'wait_seconds':diff_seconds}
    return next_trip['trip']


# Create a dictionary with the following information:
# departure_time, depart_station, end_station, duration

# trocar minutos por segundos
trip_info=[]
for trip in trips:
    trip_info.append(format_trip(trip))



    #trips.remove(trip)
    #trip = find_next_trip()

counter = 0
while len(trips) > 0:
    counter += 1
    trip = trips[0]
    while trip is not None:
        last_trip = trip
        trips.remove(trip)
        trip = find_next_possible_trip(format_trip(last_trip), trips)
print (counter)
