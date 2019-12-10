# GTFS toolkit

These tools allow you to generate all files neccesary for a TransitMapMovement-based instance from GTFS and/or OSM data.

## Requirements
* Python 3.5+ and pip installed on your machine
  
## Get started
* First create a new virtualenv:
  * `cd $THE_ONE_DIR/toolkit/gtfs`
  * `python -m venv venv`
  * `source venv/bin/activate`
* Then install dependencies:
  * `pip install -r requirements.txt`

## Create a new scenario
* Find a GTFS feed for the city you want to import into the ONE.  
  Good resources for public feeds are [transit.land/feed-registry](http://transit.land/feed-registry) or [transitfeeds.com](http://transitfeeds.com)
* Check if your selected feed includes shape data  
  (`shapes.txt` exists inside the feed and both `shapes.txt` and `stop_times.txt` need non-empty `shape_dist_traveled` columns)  
  If shape data does not exist or is incomplete it can be retreived from OpenStreetMap (See [Download map data](#Download-map-data)).  
  
  To process osm data there are currently two options:
    * Create gtfs shapes from osm data by map-matching (see [Shape Generation](#Shape-Generation))
    * Supply osm data directly to `scenario.py` to create route paths. This should be used as a fallback method when the shape enrichment was not successful as it is more error prone.
* Generate ONE settings configuration and all needed files with 
    ```
    python scenario.py myfeed.zip
    ```
    or for direct osm processing:  
    ```
    python scenario.py --osm map.osm myfeed.zip
    ```
    This will create the needed files in `$ONE/data/myfeed` and a ONE settings file at `$ONE/myfeed_settings.txt` 
    (with `$ONE` being the path of your ONE installation). 
* Switch into the ONE directory and run the scenario with
    ```
    ./one.sh myfeed_settings.txt
    ```

NOTE: the settings config contains only settings needed for the generated files. To start a valid ONE scenario a fallback `default_settings.txt` can (and must) be used to define general settings like router or time configuration.


### Options
```
positional arguments:
  gtfs_file             the GTFS feed to parse (.zip file).

optional arguments:
  -h, --help            show this help message and exit
  --osm OSM             the .osm file to match routes with if no shapes are
                        present in the gtfs feed.
  --types TYPES, -t TYPES
                        limits the the route types to parse from the gtfs
                        feed, comma separated. See https://developers.google.c
                        om/transit/gtfs/reference/#routestxt for route type
                        definitions. Defaults to 0 (tram routes)
  --weekday WEEKDAY, -d WEEKDAY
                        limits the weekdays to parse trip for from the gtfs
                        feed.Options: 0 - only working days (mo-fri), 1 - only
                        saturdays, 2 - only sundays. Defaults to 0
  --max_exceptions MAX_EXCEPTIONS, -e MAX_EXCEPTIONS
                        limits the days to parse trips for to service dates
                        with a maximum number of exceptions.This way irregular
                        service times with a lot of exceptions can be filtered
                        out. Defaults to 180 (more than half the year needs to
                        be regular)
  --nhosts NHOSTS, -n NHOSTS
                        sets the number of hosts that will be created in each
                        host group.Use "auto" to determine the minimum number
                        of hosts necessary to respect the whole schedule
                        correctly (all trips are carried out). Use this with
                        caution as it can yield to a large number of hosts in
                        your scenario. Defaults to 5.
  --name NAME, -o NAME  sets the name of this scenario. Will also be used for
                        a sub-directory containing all needed files and for
                        the file name of the settings config. Defaults to the
                        gtfs-feed filename
```

## Shape Generation
There exist multiple projects with the aim to import OSM geodata into GTFS shapes.
[ad-freiburg/pfaedle](http://github.com/ad-freiburg/pfaedle) seems to be the most reliable and best performing option that was evaluated here.
The program reads in a gtfs and osm file and attemps to create a shape for each trip of the feed via map-matching. 
To enrich your feed with pfaedle:
* install pfaedle (see the project [README](http://github.com/ad-freiburg/pfaedle) for instructions)
* extract your feed archive to a separate directory
    ```bash
    unzip gtfs.zip -d gtfs
    ```
* download osm file for your region (See OSM)
* run pfaedle
    ```bash
    pfaedle -x -D map.osm gtfs
    ```
    with `gtfs` being the directory where you extracted the feed. `-D` overwrites existing shape data. `-m` can be used to speed up the map-matching by limiting to specific types of vehicles (see project README for details)
* the extended feed will be written to `./gtfs-out`. Zip it again to make it readable by `scenario.py`
    ```bash
    zip -r -j gtfs-shaped.zip ./gtfs-out/*
    ```
    Note that the zip archive needs to contain the gtfs `txt` files at root level (`-j` parameter)

## Download map data

We can use the Overpass API for OpenStreetMap to make fine-tuned queries on the OSM dataset and retreive datasets in xml format.
The following query can be used to request all public-transport relations of a specific type within a defined area. 

```java
[out:xml][timeout:25];
// fetch area “Helsinki”, 
// change this to any city you like to query
{{geocodeArea:Helsinki}}->.searchArea;
(
  // query all tram routes
  // possible types: "train", "subway", "monorail", 
  // "tram", "light_rail", "bus", "trolleybus"
  // copy this line for any other route type you want to include
  relation["route"="tram"](area.searchArea);
);
// print results
out body;
>;
out body qt;
```

You can paste the query on [overpass-turbo](https://overpass-turbo.eu/) to get a preview of the data.
To retrieve the xml, call `https://overpass-api.de/api/interpreter?data={query}` with the query 
urlencoded or download it via the overpass-turbo website ("Export" -> "raw data directly from Overpass API").

## Example scenarios

### Helsinki

Download the feed (e.g., from [Transitfeeds](https://transitfeeds.com))
```bash
$ GTFS_DIR=$THE_ONE_DIR/toolkit/gtfs
$ cd $GTFS_DIR
$ mkdir maps
$ wget -O helsinki.zip https://transitfeeds.com/p/helsinki-regional-transport/735/latest/download
```

Download the OpenStreetMap from Helsinki (e.g., [hsl.fi/en/opendata](https://karttapalvelu.storage.hsldev.com/hsl.osm/hsl.osm.pbf))
```bash
$ wget -O helsinki.osm.pbf https://karttapalvelu.storage.hsldev.com/hsl.osm/hsl.osm.pbf
$ curl http://m.m.i24.cc/osmconvert.c | cc -x c - -lz -O3 -o osmconvert
$ ./osmconvert hsl.osm.pbf > hsl.osm
```

Or, alternatively from overpass-turbo:
```bash
$ wget -O hsl.osm https://overpass-api.de/api/interpreter?data=%5Bout%3Axml%5D%5Btimeout%3A25%5D%3Barea%283600034914%29%2D%3E%2EsearchArea%3B%28relation%5B%22route%22%3D%22tram%22%5D%28area%2EsearchArea%29%3B%29%3Bout%3B%3E%3Bout%20qt%3B%0A
```

Merge GTFS and OSM data using Pfaedle
```bash
$ unzip helsinki.zip -d maps/helsinki
$ pfaedle -x hsl.osm -D maps/helsinki
```

Create configuration files:
```bash
$ cd maps/helsinki/gtfs-out && zip hsl.zip * && cd -
$ source venv/bin/activate
$ python scenario.py maps/helsinki/gtfs-out/hsl.zip
```

Run
```bash
$ cd $THE_ONE_DIR
$ ./compile.sh 
$ ./one.sh hsl_settings.txt
```

### Cologne 

### Freiburg

### Berlin

## Known issues to be fixed

To quickly test out this toolkit you can use the GTFS feed of Helsinki and the following steps for a tram scenario:
```bash
wget -O hsl.zip https://objectstorage.fi-1.cloud.global.fujitsu.com/v1/AUTH_75240ea7e6fd4ca29b6b4b4d3d227fbe/gtfs.hsl/hsl_20130201T115528Z.zip
wget -O hsl.osm https://overpass-api.de/api/interpreter\?data\=%5Bout%3Axml%5D%5Btimeout%3A25%5D%3Barea%283600034914%29-%3E.searchArea%3B%28relation%5B%22route%22%3D%22tram%22%5D%28area.searchArea%29%3B%29%3Bout%20body%3B%3E%3Bout%20body%20qt%3B
unzip hsl.zip -d hsl
pfaedle -x hsl.osm -D --inplace hsl
zip -rj hsl_matched.zip hsl/*
python scenario.py -o helsinki hsl_matched.zip
cd ../..
./one.sh helsinki_settings.txt
```
If you get the error `Couldn't find class 'movement.TransitMapMovement'` then you probably copied over the classes of this repo without recompiling the-ONE. Run the install script and then try again.

![helsinki trams in the-ONE!](https://raw.githubusercontent.com/fcornelius/the-one/master/toolkit/gtfs/hls-screenshot.png)
