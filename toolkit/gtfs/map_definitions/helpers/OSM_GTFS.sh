#!/bin/sh
set -e

# so far the osm is either .pbf or .osm
# .pbf are expected to end in .pbf
get_osm_type(){
	[ "$#" -lt 1 ] && err_ "Wrong number of parameters. Expected: ${FUNCNAME[0]} osm_file_path"
	local OSM_FILE_COMPLETE_PATH=$1
	[[ "${OSM_source##*.}" == "pbf" ]] && echo "pbf" || echo "osm"
}

get_data(){
	[ "$#" -lt 1 ] && err_ "Wrong number of parameters. Expected: ${FUNCNAME[0]} scenario_dir"
	local SCENARIO_DIR="$1"
	local SCENARIO_NAME="${SCENARIO_DIR##*/}"
	local OSM_FILE_COMPLETE_PATH=$SCENARIO_DIR/$SCENARIO_NAME.osm
	local PBF_FILE_COMPLETE_PATH=$OSM_FILE_COMPLETE_PATH.pbf
	local GTFS_FILE_COMPLETE_PATH=$SCENARIO_DIR/$SCENARIO_NAME.zip

	sleep 10 # avoid wget 429 -- too many requests
	get_osm
	sleep 10 # avoid wget 429 -- too many requests
	get_gtfs
	merge
}

# outcome: an osm file should be put on the destnation dir
get_osm(){
	local OSM_TYPE=$(get_osm_type "$OSM_FILE_COMPLETE_PATH")
	
	if [[ "$OSM_TYPE" == "pbf" ]]; then 
        [ ! -f "osmconvert" ] && $(curl http://m.m.i24.cc/osmconvert.c | cc -x c - -lz -O3 -o osmconvert)
		wget -O "$PBF_FILE_COMPLETE_PATH" "$OSM_source"
       	./osmconvert "$PBF_FILE_COMPLETE_PATH" > $OSM_FILE_COMPLETE_PATH
	else # type == .osm
		wget -O "$OSM_FILE_COMPLETE_PATH" "$OSM_source"
	fi
	
	# verify that the OSM_FILE was created
	[ -f $OSM_FILE_COMPLETE_PATH ] || err_ "OSM was not created"
}

get_gtfs(){
	wget -O "$GTFS_FILE_COMPLETE_PATH" "$GTFS_source"

	# verify that the GTFS_FILE was created
	[ -f $GTFS_FILE_COMPLETE_PATH ] || err_ "GTFS was not created"
}

merge(){
	pushd $SCENARIO_DIR
	unzip $SCENARIO_NAME.zip
	pfaedle -D -x $SCENARIO_NAME.osm .

	# pack back into gtfs
	pushd gtfs-out
	zip $SCENARIO.zip *
	popd
	#mv gtfs-out/$SCENARIO.zip .
	#rm -rf gtfs-out
	popd
}
