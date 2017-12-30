# OfflineMapFragment
An offline (i.e., cached) map library for Android, with retina tiles support for both Google Maps and OpenStreetMaps, simply used as a wrapper for MapFragment.

A wrapper class for MapFragment from Maps SDK. 

- Provides tile caching capabilities with optional tile sources, such as OpenStreetMaps or Google Maps API. 
- Cached maps are written and read through functions supplied, so they can be stored wherever you want (or you can even ignore them and use just for OSM retina tiles, which is not available directly and thus is programmatically created by the library). 
- Networking is also performed through calling the function supplied asynchronously, hence you can use the networking library of your choice for maybe better debugging, logging, statistics etc.

Please note that the code is just experimental and for practice; and has not been used or tested by the author for any possible term-violating usages. The responsibility for avoidance of any possible violation of terms of Google Maps, OpenStreetMaps (or any other party involved) through usage of this library, belongs only to the person(s) using it such and not to the author of this code.
