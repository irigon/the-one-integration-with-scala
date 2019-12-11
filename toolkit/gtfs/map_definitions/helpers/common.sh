#!/bin/sh 
set -e

## Helper functions
usage(){
	local me=$(basename "$0")
	echo "============================"
	echo "Usage:"
	echo "	" $me " scenario_name"
	echo "============================"
}

err_(){
	echo "ERROR - $1"
	exit 1
}

section(){
	echo "============================"
	echo ""
	echo "$1"
	echo ""
	echo "============================"
}

