# create configurations based on filename and run one.sh on the create configuration

import os, shutil
from configparser import ConfigParser
from os import path
from optparse import OptionParser
from subprocess import call
import script_tools as st
from pathlib import Path


# The goal of this script is to automate the simulations in the-ONE

config = ConfigParser()
config.read('defaults.cfg')

the_one_path = config.get('path', 'the_one_path')
THE_ONE_SCRIPTS = the_one_path + "toolkit/simulation_batches/"
settings_dir = THE_ONE_SCRIPTS + "settings/"

parser = OptionParser()
parser.add_option("-i", "--input", dest="opt_input",
                  help="options file")
parser.add_option("-d", "--defaults", dest="defaults_file",
                  help="configuration file")
parser.add_option("-s", "--scenario", dest="scenario_desc", type=str,
                  help="scenario description")
parser.add_option("--dry-run", action='store_true', dest="dry_run")


(options, args) = parser.parse_args()

PROPHET_EXCLUSIVE_FIELDS=['ProphetV2Router.beta', 'ProphetV2Router.gamma']
SPRAYANDWAIT_EXCLUSIVE_FIELDS=['SprayAndWaitRouter.nrofCopies', 'SprayAndWaitRouter.binaryMode']

def backup_configuration_file(default_config, config_name):
    os.chdir(the_one_path)
    Path("reports_configuration").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(default_config, 'reports_configuration/' + config_name)

def get_template(router):
    template = "{}_router:{}_bSize:{}_Ttl:{}_Events1.size:{}_endTime:{}_warmup:{}_Events1.interval:{}_updateInt:{}_tSpeed:{}_tRange:{}_seed:{}"
    if router == 'ProphetV2Router':
        template += '_beta:{}_gamma:{}'
    elif router == 'SprayAndWaitRouter':
        template += '_SaWbin:{}_SaWcp:{}'
    return template

def extended_variable_list(d):
    l =  ['Group.router','Group.bufferSize','Group.msgTtl','Events1.size','Scenario.endTime','Report.warmup','Events1.interval','Scenario.updateInterval','btInterface.transmitSpeed','btInterface.transmitRange','MovementModel.rngSeed']
    if d['Group.router'] == 'ProphetV2Router':
        l.extend(PROPHET_EXCLUSIVE_FIELDS)
    if d['Group.router'] == 'SprayAndWaitRouter':
        l.extend(SPRAYANDWAIT_EXCLUSIVE_FIELDS)
    return l

os.chdir(the_one_path)
dst_config = settings_dir + "tmp.txt"

defaults = settings_dir + options.defaults_file
script_path = settings_dir + options.opt_input
default_settings_file = the_one_path + "default_settings.txt"
scenario_list=options.scenario_desc.split(',')
# get dict from config file
dic = st.read_config(script_path)

# return a list of dicts from the cartesian product of dic values
dict_list = st.product(dic)
ONE_variable = ['Group.router','Group.bufferSize','Group.msgTtl','Events1.size','Scenario.endTime','Report.warmup','Events1.interval','Scenario.updateInterval','btInterface.transmitSpeed','btInterface.transmitRange','MovementModel.rngSeed']


for item in dict_list:
    if item['Group.router'] != 'ProphetV2Router':
        for i in PROPHET_EXCLUSIVE_FIELDS:
            item.pop(i, None)
    if item['Group.router'] != 'SprayAndWaitRouter':
        for i in SPRAYANDWAIT_EXCLUSIVE_FIELDS:
            item.pop(i, None)


dict_list = [dict(t) for t in {tuple(d.items()) for d in dict_list}]

''' Create the output file name
'''
def get_end_name(scenario_name, variable_list, fn):
    list_of_values = [scenario_name]
    list_of_values.extend([fn(x) for x in variable_list])
    template = get_template(fn('Group.router'))
    return template.format(*list_of_values)

total   = len(dict_list)
for scenario in scenario_list:
    counter = 0
    # for each dic (item of the cartesian product)
    for entry in dict_list:
        counter += 1
        scenario_name = scenario.split('_')[0]
        # ignore simulations that were performed
        #report_name = "{}_Group.router:{}_Group.bufferSize:{}_Group.msgTtl:{}_Events1.size:{}_Scenario.endTime:{}_MovementModel.warmup:{}_Events1.interval:{}_Scenario.updateInterval:{}_MessageStatsReport.txt".format(
        name_var="{}_router:{}_bSize:{}_Ttl:{}_Events1.size:{}_endTime:{}_warmup:{}_Events1.interval:{}_updateInt:{}_beta:{}_gamma:{}_tSpeed:{}_tRange:{}_seed:{}"
        end_name = name_var.format( # end_name is used to name the reports and output data
            scenario_name,
            entry['Group.router'],
            entry['Group.bufferSize'],
            entry['Group.msgTtl'],
            entry['Events1.size'],
            entry['Scenario.endTime'],
            entry['MovementModel.warmup'],
            entry['Events1.interval'],
            entry['Scenario.updateInterval'],
            entry['ProphetV2Router.beta'],
            entry['ProphetV2Router.gamma'],
            entry['btInterface.transmitSpeed'],
            entry['btInterface.transmitRange'],
            entry['MovementModel.rngSeed'],
        )
        # scen config is used in the default configuration
        scen_config_name = name_var.format( 
            scenario_name,
            "%%Group.router%%",
            "%%Group.bufferSize%%",
            "%%Group.msgTtl%%",
            "%%Events1.size%%",
            "%%Scenario.endTime%%",
            "%%MovementModel.warmup%%",
            "%%Events1.interval%%",
            "%%Scenario.updateInterval%%",
            "%%ProphetV2Router.beta%%",
            "%%ProphetV2Router.gamma%%",
            "%%btInterface.transmitSpeed%%",
            "%%btInterface.transmitRange%%",
            "%%MovementModel.rngSeed%%",
        )


        report_name = the_one_path + "reports/" + end_name + "_MessageStatsReport.txt"
        if path.exists(report_name):
            print("Ignoring existing simulation:  {}".format(report_name))
            continue
        else:
            print(report_name + " does not exist. Simulating...")

        if options.dry_run:
            print("Dry-run concluded, exiting...")
            continue

        # touch: create the status file empty to let another process to know that this has been taken
        open(report_name, 'a').close()

        # copia para default_settings
        shutil.copyfile(defaults, default_settings_file)
        st.setValues(default_settings_file, "Scenario.name", scen_config_name)

        print("Setting values: {}".format(entry))
        for k, v in entry.items():
            st.setValues(default_settings_file, k, v)
        print('{}'.format(["./one.sh", "-b", "1", scenario]))
        # create contact plan in ContactGraphRouter
        if entry['Group.router'] == 'ContactGraphRouter':
            cplan_dir = the_one_path + 'data/contact_plans/'
            for file in os.listdir(cplan_dir):
                file_path = os.path.join(cplan_dir, file)
                try:
                    if os.path.isfile(file_path):
                        os.unlink(file_path)
                except Exception as e:
                    print(e)
            call(["./one.sh", "-b", "1", scenario])

        # backup configuration files
        backup_configuration_file(default_settings_file, end_name + "_default_settings")
        
        print("Percent: {}/{}".format(counter,total))
        call(["./one.sh", "-b", "1", scenario])

