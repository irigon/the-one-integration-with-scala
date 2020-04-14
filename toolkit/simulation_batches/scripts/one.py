# create configurations based on filename and run one.sh on the create configuration

import os, shutil
from os import path
from optparse import OptionParser
from subprocess import call
import script_tools as st
from pathlib import Path

# The goal of this script is to automate the simulations in the-ONE

THE_ONE_SCRIPTS = "/home/lento/eclipse-workspace-new/the-one-transit/toolkit/simulation_batches/"
the_one_path = "/home/lento/eclipse-workspace-new/the-one-transit/"


settings_dir = THE_ONE_SCRIPTS + "settings/"

parser = OptionParser()
parser.add_option("-i", "--input", dest="opt_input",
                  help="options file")
parser.add_option("-d", "--defaults", dest="defaults_file",
                  help="configuration file")
parser.add_option("-s", "--scenario", dest="scenario_desc", type=str,
                  help="scenario description")

(options, args) = parser.parse_args()


def backup_configuration_file(default_config, config_name):
    os.chdir(the_one_path)
    Path("reports_configuration").mkdir(parents=True, exist_ok=True)
    shutil.copyfile(default_config, 'reports_configuration/' + config_name)


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

for scenario in scenario_list:
    # for each dic (item of the cartesian product)
    for entry in dict_list:
        scenario_name = scenario.split('_')[0]
        # ignore simulations that were performed
        report_name = "{}_Group.router:{}_Group.bufferSize:{}_Group.msgTtl:{}_Events1.size:{}_MovementModel.warmup:{}_Events1.interval:{}_MessageStatsReport.txt".format(
            scenario_name,
            entry['Group.router'],
            entry['Group.bufferSize'],
            entry['Group.msgTtl'],
            entry['Events1.size'],
            entry['MovementModel.warmup'],
            entry['Events1.interval']
        )


        if path.exists(the_one_path + "reports/" + report_name):
            print("Ignoring existing simulation:  {}".format(report_name))
            continue


        # copia para default_settings
        shutil.copyfile(defaults, default_settings_file)
        complete_name = scenario_name + "_Group.router:%%Group.router%%_Group.bufferSize:%%Group.bufferSize%%_Group.msgTtl:%%Group.msgTtl%%_Events1.size:%%Events1.size%%_MovementModel.warmup:%%MovementModel.warmup%%_Events1.interval:%%Events1.interval%%"
        st.setValues(default_settings_file, "Scenario.name", complete_name)

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
        backup_configuration_file(default_settings_file, report_name + "_default_settings")
        
        call(["./one.sh", "-b", "1", scenario])

