from optparse import OptionParser
import glob, os
from collections import OrderedDict



# This script reads a directory with reports from the-ONE and prints a
# list of values splited by SEPARATOR to be later on imported by airtable

# Report directory
#REPORT_DIR="/home/lento/eclipse-workspace-new/the-one-transit/reports/"

# Separator used in the output to split values
# Unfortunately, airtable just supports .csv data and do not allow
# other separator than ','. Therefore, ',' are transformed in "-"
SEPARATOR=','

# usage: prog_name -f "file1,file2"
parser = OptionParser(usage="%prog [-d] resultFile1,resultFile2", version="%prog 0.1", 
        description="Reads directory with reports from the-ONE and prints a list of values splitted by SEPARATOR")
parser.add_option("-d", "--report_dir", dest="dir", type=str,
                  help="path to the report directory")

(options, args) = parser.parse_args()

if (not options.dir):
    parser.print_help()
    exit()
    

# Change to report directory
os.chdir(options.dir)
#file_list = os.listdir('./')
file_list = glob.glob("*MessageStatsReport.txt")

def update_dic(dic, variables):
    for var in variables:
        k,v = var.split(':')
        dic[k] = v

#print(options, args, file_list)
for i, f in enumerate(file_list):

    d_out = OrderedDict()
    for key in ['Scenario', 'router', 'bSize', 'Ttl', 'Events1.size', 'endTime', 'warmup', 'Events1.interval', 'updateInt', 'tSpeed', 'tRange', 'seed', 'beta', 'gamma', 'SaWbin', 'SaWcp']:
        d_out[key] = 'None'

    # r_* = raw variable
    fname_variables = []
    variables = f.split('_')

    for idx,v in enumerate(variables):
        if ',' in v:
            variables[idx] = '-'.join(v.split(','))


    # add to the out dict the information contained in the file name
    d_out['Scenario'] = variables[0]
    update_dic(d_out, variables[1:12])

    get_value = lambda x : x.split(':')[1]

    if d_out['router']== 'SprayAndWaitRouter':
        update_dic(d_out, variables[12:14])
    elif d_out['router']== 'ProphetV2Router':
        update_dic(d_out, variables[12:14])

    # Read file contents
    with open(f, 'r') as fd:
        lines = fd.readlines()
        # ignore the first line
        for line in lines[1:]:
            k, v = line.split(':')
            d_out[k] = v.strip()
    if i == 0:
        print(','.join(d_out.keys()))
    print(SEPARATOR.join(d_out.values()))
