# ScheduledMapRouteMovement toolkit

These tools allow you to generate all files neccesary for a ScheduledMapRouteMovement-based instance.

## Requirements
* Python 3 and pip installed on your machine
  
## Get started
* First create a new virtualenv:
  * `virtualenv venv`
  * `source venv/bin/activate`
* Then install dependencies:
  * `pip install -r requirements.txt`

## Create a new scenario

#### Retrieve map data from OpenStreetMaps:

Use the Overpass API to get osm data in xml format.
You can use this query:

```
[out:xml][timeout:25];
// fetch area “Dresden”, 
// change this to any city you like to query
{{geocodeArea:Dresden}}->.searchArea;
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

You can paste this query at [overpass-turbo](https://overpass-turbo.eu/) to get a preview of the data.
To retrieve the xml, call `https://overpass-api.de/api/interpreter?data={query}` with the query code 
urlencoded or export it via the overpass-turbo website.

Then run the script as

`python scenario.py mymap.osm`
