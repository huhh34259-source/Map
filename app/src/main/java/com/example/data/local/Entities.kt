package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val centerLat: Double,
    val centerLon: Double,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val layerId: String,
    val totalTiles: Int,
    val downloadedTiles: Int,
    val totalBytes: Long,
    val status: String, // DOWNLOADING, COMPLETE, PARTIAL, FAILED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cached_tiles",
    primaryKeys = ["layerId", "z", "x", "y"],
    indices = [Index(value = ["regionId"])]
)
data class CachedTileEntity(
    val layerId: String,
    val z: Int,
    val x: Int,
    val y: Int,
    val regionId: Long? = null,
    val filePath: String,
    val sizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String, // WATER, CAMP, SHELTER, TRAILHEAD, HAZARD, SUMMIT, CUSTOM
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "track_points")
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double,
    val speedMps: Float,
    val accuracyMeters: Float,
    val bearingDegrees: Float,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "field_intel")
data class FieldIntelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val latitude: Double,
    val longitude: Double,
    val summary: String,
    val highlights: String,
    val isPlaceholder: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)
