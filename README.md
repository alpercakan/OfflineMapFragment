# OfflineMapFragment
An offline map with support for retina maps, written in Kotlin for Android, simply used as a wrapper for MapFragment.

A wrapper class for MapFragment from Maps SDK. 

- Provides tile caching capabilities with optional tile sources, such as OpenStreetMaps or Google Maps API. 
- Cached maps are written and read through functions supplied, so they can be stored wherever you want. 
- Networking is also performed through calling the function supplied asynchronously, hence you can use the networking library of your choice for maybe better debugging, logging, statistics etc.
