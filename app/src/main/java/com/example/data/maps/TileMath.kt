package com.example.data.maps

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

data class TileCoord(val z: Int, val x: Int, val y: Int)

data class LatLonBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
)

object TileMath {
    const val TILE_SIZE_PX = 256
    private const val MAX_LAT = 85.05112878

    fun clampLat(lat: Double): Double = lat.coerceIn(-MAX_LAT, MAX_LAT)

    fun wrapLon(lon: Double): Double {
        var wrapped = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        if (wrapped == -180.0 && lon > 0) wrapped = 180.0
        return wrapped
    }

    fun lonToTileXDouble(lon: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        return ((lon + 180.0) / 360.0) * n
    }

    fun latToTileYDouble(lat: Double, zoom: Int): Double {
        val clamped = clampLat(lat)
        val latRad = Math.toRadians(clamped)
        val n = 2.0.pow(zoom)
        return (1.0 - asinh(tan(latRad)) / PI) / 2.0 * n
    }

    fun tileXToLon(x: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        return x / n * 360.0 - 180.0
    }

    fun tileYToLat(y: Double, zoom: Int): Double {
        val n = 2.0.pow(zoom)
        val latRad = atan(sinh(PI * (1.0 - 2.0 * y / n)))
        return Math.toDegrees(latRad)
    }

    fun boundsAroundPoint(centerLat: Double, centerLon: Double, radiusKm: Double): LatLonBounds {
        val latDelta = radiusKm / 110.574
        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.15)
        val lonDelta = radiusKm / (111.320 * cosLat)
        return LatLonBounds(
            minLat = clampLat(centerLat - latDelta),
            maxLat = clampLat(centerLat + latDelta),
            minLon = (centerLon - lonDelta).coerceAtLeast(-180.0),
            maxLon = (centerLon + lonDelta).coerceAtMost(180.0)
        )
    }

    fun enumerateTiles(
        bounds: LatLonBounds,
        minZoom: Int,
        maxZoom: Int,
        maxLimit: Int = 2500
    ): List<TileCoord> {
        val tiles = ArrayList<TileCoord>()
        for (z in minZoom..maxZoom) {
            val n = 1 shl z
            val xStart = floor(lonToTileXDouble(bounds.minLon, z)).toInt().coerceIn(0, n - 1)
            val xEnd = floor(lonToTileXDouble(bounds.maxLon, z)).toInt().coerceIn(0, n - 1)
            val yStart = floor(latToTileYDouble(bounds.maxLat, z)).toInt().coerceIn(0, n - 1)
            val yEnd = floor(latToTileYDouble(bounds.minLat, z)).toInt().coerceIn(0, n - 1)

            for (x in xStart..xEnd) {
                for (y in yStart..yEnd) {
                    tiles.add(TileCoord(z, x, y))
                    if (tiles.size >= maxLimit) return tiles
                }
            }
        }
        return tiles
    }

    fun estimateTileCount(bounds: LatLonBounds, minZoom: Int, maxZoom: Int): Int {
        var total = 0
        for (z in minZoom..maxZoom) {
            val n = 1 shl z
            val xStart = floor(lonToTileXDouble(bounds.minLon, z)).toInt().coerceIn(0, n - 1)
            val xEnd = floor(lonToTileXDouble(bounds.maxLon, z)).toInt().coerceIn(0, n - 1)
            val yStart = floor(latToTileYDouble(bounds.maxLat, z)).toInt().coerceIn(0, n - 1)
            val yEnd = floor(latToTileYDouble(bounds.minLat, z)).toInt().coerceIn(0, n - 1)
            total += (xEnd - xStart + 1) * (yEnd - yStart + 1)
        }
        return total
    }

    fun metersPerPixel(lat: Double, zoom: Int): Double {
        return 156543.03392 * cos(Math.toRadians(lat)) / 2.0.pow(zoom)
    }
}
