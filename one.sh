#! /bin/sh
_JAVA_OPTIONS="-Xms8G -Xmx8G" java -Xmx14000M -cp target:lib/ECLA.jar:lib/DTNConsoleConnection.jar core.DTNSim $*
