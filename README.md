# OfflineMapFragment
An offline (i.e., cached) map library for Android, with retina tiles support for both Google Maps and OpenStreetMaps, simply used as a wrapper for MapFragment.

A wrapper class for MapFragment from Maps SDK. 

- Provides tile caching capabilities with optional tile sources, such as OpenStreetMaps or Google Maps API. 
- Cached maps are written and read through functions supplied, so they can be stored wherever you want (or you can even ignore them and use just for OSM retina tiles, which is not available directly and thus is programmatically created by the library). 
- Networking is also performed through calling the function supplied asynchronously, hence you can use the networking library of your choice for maybe better debugging, logging, statistics etc.

**Please note that the code is just experimental and for practice; and has not been used or tested by the author for any possible term-violating usages. The responsibility for avoidance of any possible violation of terms of Google Maps, OpenStreetMaps (or any other party involved) through usage of this library, belongs only to the person(s) using it such and not to the author of this code.**

Features:

- Choice of tile source: Google Map or OpenStreetMaps
- Networking, cache writing & reading done through custom function supplied
- Support for cache-on-demand (i.e, cache a tile only when panned there) and regional pre-cache
- Retina maps (tiles)
- Offline estimation of cache file size of a region, in bytes
- "Pretty rectangle" calculation: Minimum enclosing rectangle of a list POIs, with given rectangle ratio

## Usage
```kotlin
OfflineMapFragmentWrapper(val mapFragment: MapFragment?,
                          val cacheWriter: (cacheName: String, data: ByteArray) -> Boolean,
                          val cacheReader: (cacheName: String) -> ByteArray?,
                          val tileFetcher: (url: String) -> ByteArray?,
                          var useRetinaTiles: Boolean = true,
                          val tileSource: TileSource = TileSource.GOOGLE_MAPS)
```

1. Implement cache writer & reader and tile fetcher. First two are responsible for saving & retrieving the given ByteArray in an associative (key, value) pair manner. Note that you can also always return `null` from the cache reader, then the wrapper will simply reduce to an non-cached map (but of course with extra functionality, such as programmatic retina creation from standard tiles). Tile fetcher is responsible for downloading the given HTTP/S URL and returning its body as a byte array. It will be called in a new thread, so you do not need to worry about networking-on-main-thread issues.

2. Simply create your MapFragment (or if it is placed in XML, find it with fragment manager), supply the wrapper with it. Then, call createMap method of the wrapper, which accepts a callback.

3. You're done!
