# Organize simulation data

Get the data spread over the-ONE outcome and organize them as a tree

DEST=/home/lento/Documents/TU-Dresden/Documents/Papers/PTS\_Simulation/Experiments/Results

$THEONEDIR/reports/\* &rarr; results  
$THEONEDIR/reports\_configuration/\* &rarr; defaults  
$THEONEDIR/$SCENARIO\_defaults.txt &rarr; specific  


Usage: prog\_name (-path path -d dest\_dir)

1. Verify whether there is a configuration file, and load the parameters if existent
2. Verify that dest\_dir does not exist, otherwise return error 
3. Create directory tree
4. Copy reports to $DEST/$name/results
5. Copy reports\_configuration to $DEST/$name/defaults/
6. Extract scenarios and copy scenario configuration to name/city\_defaults
7. If remove flag is set, clean up scenario

## Directory Tree
```
	name
	| 
	--- configurations
	| |
	|  -- city_specific
	|  -- defaults
	--- Readme.txt
	--- results
```
