package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class TileCacheStats(
    val tileCount: Int,
    val totalBytes: Long
)

@Dao
interface TerraGridDao {
    // Offline Regions
    @Query("SELECT * FROM offline_regions ORDER BY createdAt DESC")
    fun observeRegions(): Flow<List<OfflineRegionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: OfflineRegionEntity): Long

    @Query(
        "UPDATE offline_regions SET downloadedTiles = :downloadedTiles, totalBytes = :totalBytes, status = :status WHERE id = :regionId"
    )
    suspend fun updateRegionProgress(
        regionId: Long,
        downloadedTiles: Int,
        totalBytes: Long,
        status: String
    )

    @Query("DELETE FROM offline_regions WHERE id = :regionId")
    suspend fun deleteRegionById(regionId: Long)

    // Cached Tiles
    @Query("SELECT * FROM cached_tiles WHERE layerId = :layerId AND z = :z AND x = :x AND y = :y LIMIT 1")
    suspend fun getCachedTile(layerId: String, z: Int, x: Int, y: Int): CachedTileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTile(tile: CachedTileEntity)

    @Query("SELECT COUNT(*) AS tileCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes FROM cached_tiles")
    fun observeTileStats(): Flow<TileCacheStats>

    @Query("SELECT * FROM cached_tiles WHERE regionId = :regionId")
    suspend fun getTilesForRegion(regionId: Long): List<CachedTileEntity>

    @Query("DELETE FROM cached_tiles WHERE regionId = :regionId")
    suspend fun deleteTilesForRegion(regionId: Long)

    @Query("DELETE FROM cached_tiles")
    suspend fun deleteAllTiles()

    // Waypoints
    @Query("SELECT * FROM waypoints ORDER BY createdAt DESC")
    fun observeWaypoints(): Flow<List<WaypointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: WaypointEntity): Long

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteWaypointById(id: Long)

    // Track Points
    @Query("SELECT * FROM track_points WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun observeTrackPoints(sessionId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points ORDER BY timestampMs DESC LIMIT 500")
    fun observeRecentTrackPoints(): Flow<List<TrackPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackPoint(point: TrackPointEntity)

    @Query("DELETE FROM track_points")
    suspend fun clearTrackPoints()

    // Field Intel
    @Query("SELECT * FROM field_intel ORDER BY createdAt DESC LIMIT 20")
    fun observeFieldIntel(): Flow<List<FieldIntelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFieldIntel(intel: FieldIntelEntity): Long

    @Query("DELETE FROM field_intel WHERE id = :id")
    suspend fun deleteFieldIntel(id: Long)
}
