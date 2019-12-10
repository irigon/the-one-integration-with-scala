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

### get filename
if [ "$#" -ne 1 ] ; then  
	usage 
	err_ "Wrong number of parameters. Expected: scenario name"
fi


BASE_DIR="$(dirname $me)"
SCENARIO=$1
MAPS_DIR=$BASE_DIR/maps
DST_SCENARIO_DIR="$MAPS_DIR/$SCENARIO"

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

### Variable definitions
OSM_PBF=$(find . -name "$SCENARIO.osm.pbf" -print -quit)
OSM=$(find . -name "$SCENARIO.osm" -print -quit)
GTFS=$(find . -name "$SCENARIO.zip" -print -quit)

[[ -f "$OSM_PBF" || -f "$OSM" ]] || err "OSM file could not be found."
[ -f "$GTFS" ] || err "OSM file could not be found."

# create directories
mkdir -p $MAPS_DIR/$SCENARIO

# if osm is compressed, unpack
[ ! -f "osmconvert" ] && $(curl http://m.m.i24.cc/osmconvert.c | cc -x c - -lz -O3 -o osmconvert)
if [ ! -z "$OSM_BPF" ] ; then 
       ./osmconvert "$OSM_BPF" > $DST_SCENARIO_DIR/$SCENARIO.osm
else
	cp -f $OSM $DST_SCENARIO_DIR || true
fi
cp -f $GTFS "$MAPS_DIR/$SCENARIO" || true

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
