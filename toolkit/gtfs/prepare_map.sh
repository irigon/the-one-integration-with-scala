#!/bin/bash
set -e

# 1) if map exists, asks whether it should be overwritten
# 2) if map must be created 
#     2.1) obtain OSM data
#     2.2) obtain GTFS data
#     2.3) merge GTFS + OSM
# 3) create configurations
# 4) run


source map_definitions/helpers/common.sh

d=$(date +%m/%d/%Y_+%H:%M+%S)
section "Started at $d"

if [ "$#" -lt 1 ] ; then  
	usage 
	err_ "Wrong number of parameters. Expected: scenario name"
fi

BASE_DIR="$(dirname $(basename '$0'))"
SCENARIO=$1
MAPS_DIR=$BASE_DIR/maps
DST_SCENARIO_DIR="$MAPS_DIR/$SCENARIO"
DEFINITIONS=map_definitions

source $DEFINITIONS/$SCENARIO
section "--- >>> Starting $SCENARIO"

# ===========================================================
# 1) verify whether a map already exists, update if necessary
# ===========================================================
section "1) verifying whether a map already exists."

source map_definitions/helpers/overwrite_scenario.sh

ready=$(map_is_ready "$SCENARIO")
ignore=$(ignore_map "$SCENARIO")

if [[ "$ready" == "true" ]]; then
	echo "Scenario is ready, ignoring..."
	exit
fi

cleanup_if_necessary $DST_SCENARIO_DIR

EXISTS="$(map_exists $DST_SCENARIO_DIR)" 
if [[ ! "$EXISTS" == "true" ]]; then
	mkdir -p $DST_SCENARIO_DIR
	source map_definitions/helpers/OSM_GTFS.sh
	get_data "$DST_SCENARIO_DIR"
fi


section "3) Create configuration"
python scenario.py $DST_SCENARIO_DIR/$SCENARIO.zip

section "4) Compile"
pushd ../../
./compile.sh

section "5) Run"
set +e
result="Success"
./one.sh -b 1 ${SCENARIO}_settings.txt
[ "$?" -eq 0 ] || result="Failed"

section "$result"

[[ "$result" == "Success" ]] && echo $SCENARIO >> $SUCCESS_LIST

