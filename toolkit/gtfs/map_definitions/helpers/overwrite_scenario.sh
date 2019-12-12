#!/bin/sh
set -e

success_list(){
	echo map_definitions/helpers/success_list
}

search_in_list(){
	[ "$#" -lt 2 ] && err_ "Wrong number of parameters. Expected: ${FUNCNAME[0]} string_to_search list_file_name"
	local string=$1
	local list=$2

	set +e
	cat $list | grep -w $string -q
	found=$?
	set -e

	if [ "$found" -eq 0 ]; then
		echo "true"
	else
		echo "false"
	fi
}

# Maps that are already working must not be processed again
map_is_ready(){
	[ "$#" -lt 1 ] && err_ "Wrong number of parameters. Expected: ${FUNCNAME[0]} scenario_name"

	SCENARIO_NAME=$1

	search_in_list $SCENARIO_NAME "$(success_list)"
}	

ignore_map(){
	[ "$#" -lt 1 ] && err_ "Wrong number of parameters. Expected: ${FUNCNAME[0]} scenario_name"

	SCENARIO_NAME=$1
	IGNORE_LIST=map_definitions/helpers/ignore_list

	search_in_list $SCENARIO_NAME $IGNORE_LIST
}

# If a map is created, ask whether to clean it up
cleanup_if_necessary(){
	[ "$#" -lt 1 ] && err_ "Wrong number of parameters. Expected: ${FUNCNAME[0]} scenario_name"
	DST_SCENARIO_DIR=$1

	if [ -d "$DST_SCENARIO_DIR" ]; then
		echo "Scenario exists. Cleaning up..."
		rm -rf $DST_SCENARIO_DIR
	fi
}

### Does the directory exists?
map_exists() {
	[ "$#" -lt 1 ] && err_ "Wrong number of parameters. Expected: ${FUNCNAME[0]} map_name"
	DST_SCENARIO_DIR=$1
	[ -d "$DST_SCENARIO_DIR" ] && echo "true" || echo "false"
}

