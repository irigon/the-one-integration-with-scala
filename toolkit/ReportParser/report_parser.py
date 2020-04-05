from optparse import OptionParser
import os


# This script reads a directory with reports from the-ONE and prints a
# list of values splited by SEPARATOR to be later on imported by airtable

# Report directory
#REPORT_DIR="/home/lento/eclipse-workspace-new/the-one-transit/reports/"

# Separator used in the output to split values
# Unfortunately, airtable just supports .csv data and do not allow
# other separator than ','. Therefore, ',' are transformed in "-"
SEPARATOR=','

# usage: prog_name -f "file1,file2"
parser = OptionParser()
parser.add_option("-d", "--report_dir", dest="dir", type=str,
                  help="path to the report directory")

(options, args) = parser.parse_args()
# Change to report directory
os.chdir(options.dir)
file_list = os.listdir('./')


#print(options, args, file_list)
for i, f in enumerate(file_list):
    d_out = dict()
    # r_* = raw variable
    fname_variables = []
    r_name, r_router, r_buffer, r_ttl, r_msize, _ = f.split('_')

    # change ',' in r_msize to '-'
    r_msize = '-'.join(r_msize.split(','))
    # add to the out dict the information contained in the file name
    d_out['Scenario'] = r_name
    for var in [r_router, r_buffer, r_ttl, r_msize]:
        k,v = var.split(':')
        d_out[k] = v


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
