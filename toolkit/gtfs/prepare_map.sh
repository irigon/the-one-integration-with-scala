#!/bin/bash
set -ex

me=$(basename "$0")

## Helper functions
usage(){
	echo "Usage:"
	echo "	" $me " scenario_name"
}

err_(){
	echo "ERROR - $1"
	exit 1
}

get_osm(){
	_OSM="$(find . -name "$SCENARIO.osm" -print -quit)"
	[[ "$_OSM" != "" ]] && echo "$_OSM" && return
	_OSM_PBF="$(find . -name "$SCENARIO.osm.pbf" -print -quit)"
	[[ "$_OSM_PBF" != "" ]] && echo "$_OSM_PBF" && return
	echo ""
}

### get filename
if [ "$#" -lt 1 ] ; then  
	usage 
	err_ "Wrong number of parameters. Expected: scenario name"
fi


BASE_DIR="$(dirname $me)"
SCENARIO=$1
MAPS_DIR=$BASE_DIR/maps
DST_SCENARIO_DIR="$MAPS_DIR/$SCENARIO"
DEFINITIONS=map_definitions


## Import source description
source $DEFINITIONS/$SCENARIO

### Does the directory exists?
if [ -d "$DST_SCENARIO_DIR" ]; then
	read -p "$DST_SCENARIO_DIR exists. Delete it?[y/N]" -n 1 -r
	if [[ "$REPLY" =~ ^[yY]$ ]]; then
		rm -rf $DST_SCENARIO_DIR
	else 
		echo "Scenario exists. Exiting..."
		exit 1
	fi
fi

OSM="$(get_osm)"
GTFS=$(find . -name "$SCENARIO.zip" -print -quit)

# create directories
mkdir -p "$MAPS_DIR/$SCENARIO"

# If the OSM file is not declared, try to download it
# respecting its extension (.osm or .osm.pbf)
if [[ "$OSM" == "" ]] ; then
	_base=$(basename ${OSM_source})
	_extension=".${_base#*osm.}"
	[[ "$_extension" == "." ]] && _extension=""
	wget -O "$MAPS_DIR/$SCENARIO/$SCENARIO.osm${_extension}" "$OSM_source"
	OSM="$(get_osm)"
	[[ ! -f "$OSM" ]] && err "OSM file could not be found."
fi
if [[ "$GTFS" == "" ]] ; then
	extension="zip"
	wget -O "$MAPS_DIR/$SCENARIO/$SCENARIO.${extension}" "$GTFS_source"
	[ -f "$MAPS_DIR/$SCENARIO/$SCENARIO.${extension}" ] || err "GTFS file could not be found."
fi

# if osm is compressed, unpack
[ ! -f "osmconvert" ] && $(curl http://m.m.i24.cc/osmconvert.c | cc -x c - -lz -O3 -o osmconvert)
if [[ "$OSM" =~ .*pbf$  ]] ; then 
       ./osmconvert "$OSM" > $DST_SCENARIO_DIR/$SCENARIO.osm
else
	cp -f $OSM $DST_SCENARIO_DIR || true
fi
cp -f $GTFS "$MAPS_DIR/$SCENARIO" || true
exit

# Merge with pfaedle
pushd $DST_SCENARIO_DIR
unzip $SCENARIO.zip
pfaedle -D -x $SCENARIO.osm .

# pack back into gtfs
OUT_FILE=$DST_SCENARIO_DIR/gtfs-out/$SCENARIO-out.zip 
zip $OUT_FILE gtfs-out/*

# generate config files
popd

# run
python scenario.py $OUT_FILE
