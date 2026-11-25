package com.example

import com.example.data.intel.PlaceholderFieldIntelRepository
import com.example.data.local.CoordinateFormat
import com.example.data.local.SpeedUnit
import com.example.data.location.CoordinateFormatter
import com.example.data.maps.TileMath
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TerraGridCoreTest {

    @Test
    fun `coordinate formatting DD DMS DDM and Maidenhead are accurate`() {
        val lat = -7.942493
        val lon = 112.953012

        val (ddLat, ddLon) = CoordinateFormatter.formatCoordinate(lat, lon, CoordinateFormat.DD)
        assertTrue(ddLat.contains("7.942493° S"))
        assertTrue(ddLon.contains("112.953012° E"))

        val (dmsLat, dmsLon) = CoordinateFormatter.formatCoordinate(lat, lon, CoordinateFormat.DMS)
        assertTrue(dmsLat.startsWith("7°56'"))
        assertTrue(dmsLat.endsWith("\"S"))
        assertTrue(dmsLon.startsWith("112°57'"))
        assertTrue(dmsLon.endsWith("\"E"))

        val grid = CoordinateFormatter.toMaidenhead(lat, lon)
        assertEquals(6, grid.length)
    }

    @Test
    fun `speed unit conversion and haversine distance work accurately`() {
        assertEquals("36.0", CoordinateFormatter.formatSpeed(10f, SpeedUnit.KMH))
        assertEquals("22.4", CoordinateFormatter.formatSpeed(10f, SpeedUnit.MPH))

        // ~111.19 km per 1 degree latitude
        val dist = CoordinateFormatter.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(abs(dist - 111195.0) < 300.0)

        val bearing = CoordinateFormatter.bearingDegrees(0.0, 0.0, 1.0, 0.0)
        assertTrue(bearing < 0.5f || bearing > 359.5f)
        assertEquals("N", CoordinateFormatter.cardinalDirection(bearing))
    }

    @Test
    fun `tile math projection round-trip and region enumeration`() {
        val lat = -7.942493
        val lon = 112.953012
        val z = 13

        val x = TileMath.lonToTileXDouble(lon, z)
        val y = TileMath.latToTileYDouble(lat, z)

        val latBack = TileMath.tileYToLat(y, z)
        val lonBack = TileMath.tileXToLon(x, z)

        assertTrue(abs(lat - latBack) < 0.0001)
        assertTrue(abs(lon - lonBack) < 0.0001)

        val bounds = TileMath.boundsAroundPoint(lat, lon, 2.0)
        val tiles = TileMath.enumerateTiles(bounds, 10, 12)
        assertTrue(tiles.isNotEmpty())
        assertEquals(TileMath.estimateTileCount(bounds, 10, 12), tiles.size)
    }

    @Test
    fun `placeholder field intel repository flags placeholder state`() = runTest {
        val repo = PlaceholderFieldIntelRepository()
        val result = repo.queryAreaIntel(-7.942493, 112.953012, "Water & Springs")
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertTrue(report.isPlaceholder)
        assertTrue(report.places.isNotEmpty())
    }
}
