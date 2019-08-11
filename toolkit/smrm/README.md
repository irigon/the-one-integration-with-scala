# GTFS toolkit

These tools allow you to generate all files neccesary for a TransitMapMovement-based instance from GTFS and/or OSM data.

## Requirements
* Python 3.5+ and pip installed on your machine
  
## Get started
* First create a new virtualenv:
  * `virtualenv venv`
  * `source venv/bin/activate`
* Then install dependencies:
  * `pip install -r requirements.txt`

## Create a new scenario
* Find a GTFS feed for the city you want to import into the ONE. Good resources for public feeds are [transit.land/feed-registry]() or [transitfeeds.com]()
* Check if your selected feed includes shape data (`shapes.txt` exists inside the feed zip and is populated). If shape data does not exist it can be retreived from OpenStreetMap (See [Download map data](#Download-map-data)). To process osm data there are currently two options:
    * Create gtfs shapes from osm data by map-matching (see [Shape Generation](#Shape-Generation))
    * Supply osm data directly to scenario.py to create route paths. This should be used as a fallback method when the shape enrichment was not successful as it is more error prone.
* Generate ONE settings configuration and all needed files with 
    ```
    python scenario.py myfeed.gtfs
    ```
    or for direct osm processing:
    ```
    python scenario.py --osm map.osm myfeed.zip
    ```
    This will create the needed files in `$ONE/data/myfeed` and a ONE settings file at `$ONE/myfeed_settings.txt` 
    (with `$ONE` being the path of your ONE installation). Then switch into the repository root and run
* Switch into the ONE directory and run the scenario with
    ```
    cd ..
    ./one.sh myfeed_settings.txt
    ```

NOTE: the generated settings file contains only settings needed for the generated files. To start a valid ONE scenario a fallback `default_settings.txt` can (and must) be used to define general settings like router or time options.


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
                        feed, comma separated. See https://developers.google.com/transit/gtfs/reference/#routestxt
                        for route type definitions. Defaults to 0 (tram routes)
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

```

## Shape Generation
There exist multiple projects with the aim to import OSM geodata into GTFS shapes.
[ad-freiburg/pfaedle](github.com/ad-freiburg/pfaedle) seems to be the most reliable and best performing option that was evaluated here.
The program reads in a gtfs and osm file and attemps to create a shape for each trip of the feed via map-matching. 
To enrich your feed with pfaedle:
* install pfaedle (see the project [README](github.com/ad-freiburg/pfaedle) for instructions)
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


