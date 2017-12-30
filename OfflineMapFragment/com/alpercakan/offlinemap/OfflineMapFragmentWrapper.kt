// Created by Alper Çakan
// Version: 1.0
// Date: 15 Jul 2017

package com.alpercakan.offlinemap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileOverlayOptions
import java.io.ByteArrayOutputStream

/**
 * Tile sizes
 */
private val STANDARD_TILE_WIDTH = 256 // px
private val STANDARD_TILE_HEIGHT = 256 // px
private val RETINA_TILE_WIDTH = 512 // px
private val RETINA_TILE_HEIGHT = 512 // px

private val AVERAGE_STANDARD_TILE_SIZE = 20480 // bytes
private val AVERAGE_RETINA_TILE_SIZE = 143360 // bytes

private val DEFAULT_ZOOM_LEVEL_FOR_REGIONAL_CACHES = 17

/**
 * A wrapper class for MapFragment from Maps SDK.
 *
 * Provides tile caching capabilities with optional tile sources, such as OSM or Google Maps API.
 *
 * @param mapFragment Map fragment object to be wrapped. Provide null if the wrapper will be used
 * for tasks like pre-caching or cache cleaning; tasks which do not require an MapFragment object.
 * @param cacheReader A function which will return the cached tile as ByteArray, on success; and
 * will return null on failure.
 * @param cacheWriter A function which will save the cached tile which is provided as ByteArray. The
 * function must return true on success, false on failure.
 * @param tileFetcher A function which can retrieve binary data as ByteArray from the given URL.
 * Since MapFragment already retrieves tiles in a new thread, tileFetcher function must be
 * blocking (synchronized).
 */
class OfflineMapFragmentWrapper(val mapFragment: MapFragment?,
                                val cacheWriter: (cacheName: String, data: ByteArray) -> Boolean,
                                val cacheReader: (cacheName: String) -> ByteArray?,
                                val tileFetcher: (url: String) -> ByteArray?,
                                var useRetinaTiles: Boolean = true,
                                val tileSource: TileSource = TileSource.GOOGLE_MAPS) {
    enum class TileSource { GOOGLE_MAPS, OPEN_STREET_MAP }

    var map: GoogleMap? = null
        private set

    /**
     * Creates and properly wraps the map.
     * @param onMapReadyCallback Function to be called when the map is ready to be used.
     */
    fun createMap(onMapReadyCallback: (() -> Unit)? = null) {
        if (mapFragment == null) {
            throw IllegalStateException()
        }

        mapFragment.getMapAsync {
            map = it

            // Since we will be providing rendered tiles, map itself should not render anything.
            it.mapType = GoogleMap.MAP_TYPE_NONE

            it.addTileOverlay(TileOverlayOptions().tileProvider {
                x: Int, y: Int, zoom: Int ->
                getTile(x, y, zoom, useRetinaTiles)
            })

            if (onMapReadyCallback != null)
                onMapReadyCallback()
        }
    }

    /**
     * Throws an appropriate exception if given tile parameters are illegal.
     * @param x Tile columnn
     * @param y Tile row
     * @param zoom Zoom level
     */
    private fun throwIfTileParamsIllegal(x: Int, y: Int, zoom: Int) {
        // Max zoom level depends on position. Although not a tight one, 29 is a safe bound.
        if (zoom !in 0..29)
            throw IllegalArgumentException("Zoom level exceeds bounds.")

        if (x !in 0 until (1L shl zoom /* 2^zoom */))
            throw IllegalArgumentException("Invalid column number")

        if (y !in 0 until (1L shl zoom /* 2^zoom */))
            throw IllegalArgumentException("Invalid row number")
    }

    /**
     * Creates a name for the tile.
     * @param x Column number of the tile. Must be in the range [0, 2^zoom - 1]
     * @param y Row number of the file. Must be in the range [0, 2^zoom - 1]
     * @param zoom Zoom level. Must be greater than 0. Maximum value depends on position.
     * @return Tile name
     */
    private fun createTileName(x: Int, y: Int, zoom: Int, retina: Boolean = false): String {
        throwIfTileParamsIllegal(x, y, zoom)

        return "${if (retina) "r" else "s"}-OfflineMapFragmentWrapper-tilecache-$x-$y-$zoom"
    }

    /**
     * Fetches a tile from the source.
     * @param x Column number of the tile. Must be in the range [0, 2^zoom - 1]
     * @param y Row number of the file. Must be in the range [0, 2^zoom - 1]
     * @param zoom Zoom level. Must be greater than 0. Maximum value depends on position.
     * @return Data on success, null on failure
     */
    private fun fetchTile(x: Int, y: Int, zoom: Int, retina: Boolean = false): ByteArray? {
        return if (retina) {
            fetchRetinaTile(x, y, zoom)
        } else {
            tileFetcher(
                    when (tileSource) {
                        TileSource.GOOGLE_MAPS -> "http://mt3.google.com/vt/lyrs=m&x=$x&y=$y&z=$zoom"
                        TileSource.OPEN_STREET_MAP -> "http://a.tile.openstreetmap.org/$zoom/$x/$y.png"
                    })
        }
    }

    /**
     * Caches a tile.
     * @param x Column number of the tile. Must be in the range [0, 2^zoom - 1]
     * @param y Row number of the file. Must be in the range [0, 2^zoom - 1]
     * @param zoom Zoom level. Must be greater than 0. Maximum value depends on position.
     * @return True on success, false on failure
     */
    private fun cacheTile(x: Int, y: Int, zoom: Int, retina: Boolean = false): Boolean {
        throwIfTileParamsIllegal(x, y, zoom)

        val data = fetchTile(x, y, zoom, retina)

        if (data == null) {
            return false
        }

        return cacheWriter(createTileName(x, y, zoom, retina), data)
    }

    /**
     * Caches all tiles for the given rectangular region.
     *
     * Since tileFetcher is a blocking network function, this function should never be called on
     * the main thread.
     *
     * @param bounds Geographical bounds of the region
     * @param minZoomLevel Minimum zoom level
     * @param maxZoomLevel Maximum zoom level
     */
    fun cacheRegion(bounds: LatLngBounds, minZoomLevel: Int, maxZoomLevel: Int): Boolean {
        var ret = true

        for (zoomLevel in minZoomLevel..maxZoomLevel) {
            if (!cacheRegion(bounds, zoomLevel)) {
                ret = false
            }
        }

        return ret
    }

    /**
     * Caches all tiles of the given zoom level for the given rectangular region.
     *
     * Since tileFetcher is a blocking network function, this function should never be called on
     * the main thread.
     *
     * @param bounds Geographical bounds of the region
     * @param zoomLevel Zoom level for caching
     */
    fun cacheRegion(bounds: LatLngBounds, zoomLevel: Int = DEFAULT_ZOOM_LEVEL_FOR_REGIONAL_CACHES)
            : Boolean {
        var ret = true

        val southwestTileParam = getTileNumber(bounds.southwest.latitude,
                bounds.southwest.longitude,
                zoomLevel)
        val northeastTileParam = getTileNumber(bounds.northeast.latitude,
                bounds.northeast.longitude,
                zoomLevel)

        for (x in (southwestTileParam.first)..(northeastTileParam.first)) {
            for (y in (northeastTileParam.second)..(southwestTileParam.second)) {
                if (!cacheTile(x, y, zoomLevel, useRetinaTiles))
                    ret = false
            }
        }

        return ret
    }

    /**
     * Retrieves a tile from cache.
     * @param x Column number of the tile. Must be in the range [0, 2^zoom - 1]
     * @param y Row number of the file. Must be in the range [0, 2^zoom - 1]
     * @param zoom Zoom level. Must be greater than 0. Maximum value depends on position.
     * @return Null if cached tile could not be retrieved (probably cache does not exist for that
     * file); tile itself otherwise.
     */
    private fun getCachedTile(x: Int, y: Int, zoom: Int, retina: Boolean): Tile? {
        throwIfTileParamsIllegal(x, y, zoom)

        var data = cacheReader(createTileName(x, y, zoom, retina))

        if (data == null) {
            return null
        } else {
            return if (retina) Tile(STANDARD_TILE_WIDTH, STANDARD_TILE_HEIGHT, data)
            else Tile(RETINA_TILE_WIDTH, RETINA_TILE_HEIGHT, data)
        }
    }

    /**
     * Obtains a standard quality tile.
     * @param x Column number of the tile. Must be in the range [0, 2^zoom - 1]
     * @param y Row number of the file. Must be in the range [0, 2^zoom - 1]
     * @param zoom Zoom level. Must be greater than 0. Maximum value depends on position.
     * @return Null if the tile can not be provided; tile itself otherwise.
     */
    private fun getTile(x: Int, y: Int, zoom: Int, retina: Boolean = false): Tile? {
        throwIfTileParamsIllegal(x, y, zoom)

        var tile = getCachedTile(x, y, zoom, retina)

        if (tile == null) {
            cacheTile(x, y, zoom, retina)
            tile = getCachedTile(x, y, zoom, retina)
        }

        return tile
    }

    /**
     * Obtains a high quality tile.
     * @param x Column number of the tile. Must be in the range [0, 2^zoom - 1]
     * @param y Row number of the file. Must be in the range [0, 2^zoom - 1]
     * @param zoom Zoom level. Must be greater than 0. Maximum value depends on position.
     * @return Null if the tile can not be provided; tile itself otherwise.
     */
    private fun fetchRetinaTile(x: Int, y: Int, zoom: Int): ByteArray? {
        throwIfTileParamsIllegal(x, y, zoom)

        val tile1 = fetchTile(2 * x + 1, 2 * y, zoom + 1)
        val tile2 = fetchTile(2 * x, 2 * y, zoom + 1)
        val tile3 = fetchTile(2 * x, 2 * y + 1, zoom + 1)
        val tile4 = fetchTile(2 * x + 1, 2 * y + 1, zoom + 1)

        if (tile1 == null || tile2 == null || tile3 == null || tile4 == null) {
            return null
        }

        var retinaTileImage = mergeTileImages(tile1, tile2, tile3, tile4,
                RETINA_TILE_WIDTH, RETINA_TILE_HEIGHT)

        return retinaTileImage
    }

    /**
     * Merges given tile images into one photo.
     *
     * All given images must be the same width and the same height.
     *
     * ---------
     * | 2 | 1 |
     * |-------|
     * | 3 | 4 |
     * ---------
     *
     * @return New photo on success, null on failure
     */
    private fun mergeTileImages(tileImage1: ByteArray, tileImage2: ByteArray,
                                tileImage3: ByteArray, tileImage4: ByteArray,
                                mergedWidth: Int, mergedHeight: Int): ByteArray? {
        val bitmap1 = BitmapFactory.decodeByteArray(tileImage1, 0, tileImage1.size)
        val bitmap2 = BitmapFactory.decodeByteArray(tileImage2, 0, tileImage2.size)
        val bitmap3 = BitmapFactory.decodeByteArray(tileImage3, 0, tileImage3.size)
        val bitmap4 = BitmapFactory.decodeByteArray(tileImage4, 0, tileImage4.size)
        val retinaBitmap = Bitmap.createBitmap(mergedWidth, mergedHeight, bitmap1.config)

        if (bitmap1 == null || bitmap2 == null || bitmap3 == null ||
                bitmap4 == null || retinaBitmap == null) {
            return null
        }

        val canvas = Canvas(retinaBitmap)

        canvas.drawBitmap(bitmap1, null,
                Rect(bitmap1.width, 0, bitmap1.width * 2, bitmap1.height),
                null)

        canvas.drawBitmap(bitmap2, null,
                Rect(0, 0, bitmap1.width, bitmap1.height),
                null)
        canvas.drawBitmap(bitmap3, null,
                Rect(0, bitmap1.height, bitmap1.width, bitmap1.height * 2),
                null)
        canvas.drawBitmap(bitmap4, null,
                Rect(bitmap1.width, bitmap1.height, bitmap1.width * 2, bitmap1.height * 2),
                null)

        val byteStream = ByteArrayOutputStream()

        retinaBitmap.compress(Bitmap.CompressFormat.PNG, 100 /* ignored for PNG */, byteStream)

        return byteStream.toByteArray()
    }

    companion object {
        /**
         * Calculates a "pretty" rectangle which is co-centered with the smallest enclosing rectangle of
         * the given POI set. The "pretty" rectangle has lengths calculated from the enclosing
         * rectangle using the given ratios.
         *
         * Not safe to use if area is near Mercator bounds and/or area is too big and/or scale(s) is/are
         * too big.
         *
         * @param poiList List of POIs
         * @param latRatio Rectangle scaling factor in latitude (y) direction. Must be > 1.
         * @param lngRatio Rectangle scaling factor in longitude (x) direction. Must be > 1.
         * @return "Pretty" rectangles bounds
         */
        // TODO oran deÄŸiÅŸebilir.
        fun calculatePrettyRectangle(poiList: MutableList<LatLng>,
                                     latRatio: Double = 1.0,
                                     lngRatio: Double = 1.0): LatLngBounds {

            if (poiList.size == 0) {
                LatLngBounds(LatLng(0.0, 0.0), LatLng(1.0, 1.0))
            }

            val encRectSouthLat = poiList.minBy { it.latitude }!!.latitude
            val encRectNorthLat = poiList.maxBy { it.latitude }!!.latitude
            val encRectWestLng = poiList.minBy { it.longitude }!!.longitude
            val encRectEastLng = poiList.maxBy { it.longitude }!!.longitude

            var latLength = (encRectNorthLat - encRectSouthLat) * latRatio
            var lngLength = (encRectEastLng - encRectWestLng) * lngRatio

            if (lngLength * 1.18 > latLength) {
                latLength = lngLength * 1.18
            } else if (latLength / 1.18 > lngLength) {
                lngLength = latLength / 1.18
            }

            return makeRect(
                    LatLngBounds(LatLng(encRectSouthLat, encRectWestLng),
                            LatLng(encRectNorthLat, encRectEastLng)).center,
                    latLength, lngLength)
        }

        fun makeRect(center: LatLng, latLength: Double, lngLength: Double) =
                LatLngBounds(
                        LatLng(center.latitude - latLength / 2, center.longitude - lngLength / 2),
                        LatLng(center.latitude + latLength / 2, center.longitude + lngLength / 2))


        /**
         * Calculates row and column of the tile which bounds the given point on the given zoom level.
         * @return (x, y)
         */
        fun getTileNumber(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
            var x = Math.floor((lon + 180) / 360 * (1 shl zoom)).toInt()
            var y = Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1
                    / Math.cos(Math.toRadians(lat))) / Math.PI) / 2
                    * (1 shl zoom)).toInt()
            if (x < 0)
                x = 0
            if (x >= 1 shl zoom)
                x = (1 shl zoom) - 1
            if (y < 0)
                y = 0
            if (y >= 1 shl zoom)
                y = (1 shl zoom) - 1

            return Pair(x, y)
        }

        /**
         * Estimates the size of the region cache files
         * @param bounds Geographical bounds of the region
         * @param minZoomLevel Minimum zoom level
         * @param maxZoomLevel Maximum zoom level
         * @param downloadSize If true, download size for the cache files will be estimated. If false,
         * storage size will be estimated.
         * @return Estimated size of the region cache files in bytes
         */
        fun estimateRegionCacheSize(bounds: LatLngBounds,
                                    minZoomLevel: Int,
                                    maxZoomLevel: Int,
                                    useRetinaTiles: Boolean = true,
                                    downloadSize: Boolean = true): Long {
            return calculateTileCount(bounds, minZoomLevel, maxZoomLevel) *
                    (if (useRetinaTiles)
                        (if (downloadSize) (AVERAGE_STANDARD_TILE_SIZE * 4)
                        else AVERAGE_RETINA_TILE_SIZE)
                    else AVERAGE_STANDARD_TILE_SIZE)
        }

        /**
         * Calculates the number of tiles in given region
         * @param bounds Geographical bounds of the region
         * @param minZoomLevel Minimum zoom level
         * @param maxZoomLevel Maximum zoom level
         * @return Calculated number of tiles (precise)
         */
        fun calculateTileCount(bounds: LatLngBounds,
                               minZoomLevel: Int,
                               maxZoomLevel: Int): Long {
            var total: Long = 0

            for (zoomLevel in minZoomLevel..maxZoomLevel) {
                val southwestTileParam = getTileNumber(bounds.southwest.latitude,
                        bounds.southwest.longitude,
                        zoomLevel)
                val northeastTileParam = getTileNumber(bounds.northeast.latitude,
                        bounds.northeast.longitude,
                        zoomLevel)

                total += (northeastTileParam.first - southwestTileParam.first + 1) *
                        (southwestTileParam.second - northeastTileParam.second + 1)
            }

            return total
        }
    }
}
