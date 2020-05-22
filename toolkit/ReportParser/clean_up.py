from distutils import dir_util
import os, sys
from optparse import OptionParser
from os import path
import shutil

parser = OptionParser()
# both options can be given in a text file "options.txt in this directory"
parser.add_option('-p', '--the_one_path', dest='the_one_path', type='str', help="path to the-ONE directory")
parser.add_option('-r', '--result_dir', dest='result_dir', type='str', help="path to results directory")
parser.add_option('-d', '--dest', dest='dest', type='str', help="path to destination directory")
parser.add_option('-f', '--force', action='store_true', help="delete tree if existent")

input_vars={}

def set_variable(conf_line):
   global input_vars
   if not conf_line.startswith('#'):
      name, value = conf_line.split('=')
      input_vars[name]=value.rstrip()

def create_tree (in_d) :

    for d in [in_d['dest'],
              in_d['dest'] + "/configurations",
              in_d['dest'] + "/configurations/city_specific",
              in_d['dest'] + "/configurations/defaults",
              in_d['dest'] + "/results"]:
        os.makedirs(d, 0o755, exist_ok=True)


if path.exists("options.cnf"):
   with open("options.cnf", 'r') as fp:
      lines = fp.readlines()
      for line in lines:
         set_variable(line)

(option, args) = parser.parse_args()

if 'result_dir' in option.__dict__.keys() and option.result_dir is not None:
   input_vars['result_dir']=option.result_dir
if 'dest' in option.__dict__.keys() and option.dest is not None:
   input_vars['dest']=option.dest
if 'the_one_path' in option.__dict__.keys() and option.the_one_path is not None:
   input_vars['the_one_path']=option.path


# Ensure that 'path' is valid and 'dest' dir does not exist
assert ('the_one_path' in input_vars.keys() and 'dest' in input_vars.keys())
assert(path.exists(input_vars['the_one_path'])
       and path.exists(input_vars['result_dir']))


dst_dir = input_vars['result_dir'] + '/' + input_vars['dest']

if option.force:
    print("Forcing")

if path.exists(dst_dir):
    print("ERROR: Directory exists")
    print("{}".format(dst_dir))
    exit(1)

CITY_SPECIFIC  = dst_dir + "/configurations/city_specific"
DEFAULTS       = dst_dir + "/configurations/defaults"
RESULT_DIR     = dst_dir + "/results"
REPORT_SRC_DIR = input_vars['the_one_path'] + '/reports'
CONFIG_SRC_DIR = input_vars['the_one_path'] + '/reports_configuration'

# CREATE DIRECTORY TREE
os.chdir(input_vars['result_dir'])
create_tree(input_vars)


# cp reports to $dst/reports
dir_util.copy_tree( REPORT_SRC_DIR , RESULT_DIR)

# cp default configuration to $dst/config
dir_util.copy_tree(CONFIG_SRC_DIR, DEFAULTS)

# cp specific

print("Path:{}".format(input_vars['the_one_path']))
print("Result_dir:{}".format(input_vars['result_dir']))
print("Destination Directory:{}".format(input_vars['dest']))






