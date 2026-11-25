package com.example.ui

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.intel.FieldIntelReport
import com.example.data.intel.FieldIntelRepository
import com.example.data.intel.PlaceholderFieldIntelRepository
import com.example.data.local.CoordinateFormat
import com.example.data.local.FieldIntelEntity
import com.example.data.local.MapLayer
import com.example.data.local.OfflineRegionEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.SpeedUnit
import com.example.data.local.TerraGridDatabase
import com.example.data.local.TileCacheStats
import com.example.data.local.TrackPointEntity
import com.example.data.local.UserPreferences
import com.example.data.local.WaypointEntity
import com.example.data.location.CoordinateFormatter
import com.example.data.location.LocationTelemetryManager
import com.example.data.location.TelemetrySnapshot
import com.example.data.maps.RegionDownloadProgress
import com.example.data.maps.TileCacheRepository
import com.example.data.maps.TileMath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripMetrics(
    val isRecording: Boolean = false,
    val sessionId: Long = System.currentTimeMillis(),
    val distanceMeters: Double = 0.0,
    val maxSpeedMps: Float = 0f,
    val avgSpeedMps: Float = 0f,
    val elapsedMs: Long = 0L,
    val recordedPointsCount: Int = 0
)

data class MapViewportState(
    // Default initial overview center before first GNSS fix (Mount Bromo / Tengger caldera)
    val centerLat: Double = -7.942493,
    val centerLon: Double = 112.953012,
    val zoom: Int = 13,
    val followGps: Boolean = true
)

sealed interface IntelUiState {
    data object Idle : IntelUiState
    data object Loading : IntelUiState
    data class Ready(val report: FieldIntelReport) : IntelUiState
    data class Error(val message: String) : IntelUiState
}

data class RemoteAreaPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double
)

class TerraGridViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TerraGridDatabase.getInstance(application)
    private val dao = database.dao()
    private val settingsStore = SettingsDataStore(application)
    val telemetryManager = LocationTelemetryManager(application)
    val tileRepository = TileCacheRepository(application, dao)
    private val intelRepository: FieldIntelRepository = PlaceholderFieldIntelRepository()

    val remotePresets = listOf(
        RemoteAreaPreset("bromo", "Bromo Tengger Caldera", "East Java Volcanic Basin", -7.942493, 112.953012),
        RemoteAreaPreset("semeru", "Mount Semeru Ridge", "Ranu Kumbolo Alpine Trail", -8.049300, 112.919900),
        RemoteAreaPreset("rinjani", "Rinjani Crater Rim", "Lombok High-Altitude Ridge", -8.411300, 116.457300),
        RemoteAreaPreset("yosemite", "Yosemite Backcountry", "High Sierra Granite Valley", 37.745917, -119.533199)
    )

    val telemetry: StateFlow<TelemetrySnapshot> = telemetryManager.telemetry

    val preferences: StateFlow<UserPreferences> = settingsStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    val offlineRegions: StateFlow<List<OfflineRegionEntity>> = dao.observeRegions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tileStats: StateFlow<TileCacheStats> = dao.observeTileStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TileCacheStats(0, 0L))

    val waypoints: StateFlow<List<WaypointEntity>> = dao.observeWaypoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trackPoints: StateFlow<List<TrackPointEntity>> = dao.observeRecentTrackPoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedIntel: StateFlow<List<FieldIntelEntity>> = dao.observeFieldIntel()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadProgress: StateFlow<RegionDownloadProgress?> = tileRepository.downloadProgress

    private val _tripMetrics = MutableStateFlow(TripMetrics())
    val tripMetrics: StateFlow<TripMetrics> = _tripMetrics.asStateFlow()

    private val _mapViewport = MutableStateFlow(MapViewportState())
    val mapViewport: StateFlow<MapViewportState> = _mapViewport.asStateFlow()

    private val _intelState = MutableStateFlow<IntelUiState>(IntelUiState.Idle)
    val intelState: StateFlow<IntelUiState> = _intelState.asStateFlow()

    private var lastRecordedLat: Double? = null
    private var lastRecordedLon: Double? = null
    private var tripStartBaseMs: Long = 0L
    private var accumulatedPausedMs: Long = 0L
    private var hasCenteredOnFirstFix = false

    init {
        telemetryManager.startPassiveSensorsAndNetwork()

        // Observe live GNSS updates for trip distance/speed tracking & auto-follow map
        viewModelScope.launch {
            telemetry.collect { snap ->
                val lat = snap.latitude
                val lon = snap.longitude
                if (lat != null && lon != null) {
                    if (!hasCenteredOnFirstFix || _mapViewport.value.followGps) {
                        hasCenteredOnFirstFix = true
                        _mapViewport.update { it.copy(centerLat = lat, centerLon = lon) }
                    }
                    handleTripLocationUpdate(snap)
                }
            }
        }

        // Trip duration ticker while recording
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                val trip = _tripMetrics.value
                if (trip.isRecording) {
                    val elapsed = accumulatedPausedMs + (System.currentTimeMillis() - tripStartBaseMs)
                    val avgMps = if (elapsed > 1500L) {
                        (trip.distanceMeters / (elapsed / 1000.0)).toFloat()
                    } else 0f
                    _tripMetrics.update {
                        it.copy(elapsedMs = elapsed, avgSpeedMps = avgMps)
                    }
                }
            }
        }
    }

    private suspend fun handleTripLocationUpdate(snap: TelemetrySnapshot) {
        val trip = _tripMetrics.value
        if (!trip.isRecording) return
        val lat = snap.latitude ?: return
        val lon = snap.longitude ?: return

        val prevLat = lastRecordedLat
        val prevLon = lastRecordedLon
        var addedDistance = 0.0
        if (prevLat != null && prevLon != null) {
            val dist = CoordinateFormatter.haversineMeters(prevLat, prevLon, lat, lon)
            // Ignore tiny jitter when accuracy is coarse, accept >= 3m real displacement
            if (dist >= 3.0) {
                addedDistance = dist
                lastRecordedLat = lat
                lastRecordedLon = lon
                dao.insertTrackPoint(
                    TrackPointEntity(
                        sessionId = trip.sessionId,
                        latitude = lat,
                        longitude = lon,
                        altitudeMeters = snap.altitudeMeters ?: 0.0,
                        speedMps = snap.speedMps,
                        accuracyMeters = snap.accuracyMeters ?: 0f,
                        bearingDegrees = snap.gpsBearingDegrees ?: snap.compassAzimuthDegrees
                    )
                )
            }
        } else {
            lastRecordedLat = lat
            lastRecordedLon = lon
            dao.insertTrackPoint(
                TrackPointEntity(
                    sessionId = trip.sessionId,
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = snap.altitudeMeters ?: 0.0,
                    speedMps = snap.speedMps,
                    accuracyMeters = snap.accuracyMeters ?: 0f,
                    bearingDegrees = snap.gpsBearingDegrees ?: snap.compassAzimuthDegrees
                )
            )
        }

        _tripMetrics.update { current ->
            val newDist = current.distanceMeters + addedDistance
            val newMax = maxOf(current.maxSpeedMps, snap.speedMps)
            val newCount = if (addedDistance > 0 || current.recordedPointsCount == 0) {
                current.recordedPointsCount + 1
            } else {
                current.recordedPointsCount
            }
            current.copy(
                distanceMeters = newDist,
                maxSpeedMps = newMax,
                recordedPointsCount = newCount
            )
        }
    }

    fun toggleTripRecording() {
        val current = _tripMetrics.value
        if (current.isRecording) {
            accumulatedPausedMs += System.currentTimeMillis() - tripStartBaseMs
            _tripMetrics.update { it.copy(isRecording = false) }
        } else {
            tripStartBaseMs = System.currentTimeMillis()
            _tripMetrics.update { it.copy(isRecording = true) }
        }
    }

    fun resetTrip() {
        accumulatedPausedMs = 0L
        tripStartBaseMs = System.currentTimeMillis()
        lastRecordedLat = null
        lastRecordedLon = null
        _tripMetrics.value = TripMetrics(isRecording = false, sessionId = System.currentTimeMillis())
        viewModelScope.launch {
            dao.clearTrackPoints()
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            telemetryManager.startLocationUpdates()
        } else {
            telemetryManager.onPermissionDenied()
        }
    }

    // Map Viewport Controls
    fun panMapByPixels(deltaXPx: Float, deltaYPx: Float, tileDisplayPx: Float) {
        _mapViewport.update { vp ->
            val worldTiles = (1 shl vp.zoom).toDouble()
            val x = TileMath.lonToTileXDouble(vp.centerLon, vp.zoom) - (deltaXPx / tileDisplayPx)
            val y = TileMath.latToTileYDouble(vp.centerLat, vp.zoom) - (deltaYPx / tileDisplayPx)
            val clampedY = y.coerceIn(0.5, worldTiles - 0.5)
            val wrappedX = ((x % worldTiles) + worldTiles) % worldTiles
            vp.copy(
                centerLat = TileMath.tileYToLat(clampedY, vp.zoom),
                centerLon = TileMath.tileXToLon(wrappedX, vp.zoom),
                followGps = false
            )
        }
    }

    fun setMapZoom(newZoom: Int) {
        val clamped = newZoom.coerceIn(3, 17)
        _mapViewport.update { it.copy(zoom = clamped) }
    }

    fun centerMapOnGps() {
        val snap = telemetry.value
        if (snap.latitude != null && snap.longitude != null) {
            _mapViewport.update {
                it.copy(centerLat = snap.latitude, centerLon = snap.longitude, followGps = true)
            }
        } else {
            _mapViewport.update { it.copy(followGps = true) }
        }
    }

    fun jumpToCoordinate(lat: Double, lon: Double, zoom: Int = 13) {
        _mapViewport.value = MapViewportState(
            centerLat = lat,
            centerLon = lon,
            zoom = zoom.coerceIn(3, 17),
            followGps = false
        )
    }

    suspend fun loadMapTile(layer: MapLayer, z: Int, x: Int, y: Int): ImageBitmap? {
        val prefs = preferences.value
        val online = telemetry.value.isNetworkOnline && !prefs.offlineOnlyMode
        return tileRepository.getOrFetchTile(layer, z, x, y, allowNetwork = online)
    }

    fun downloadOfflineRegion(
        name: String,
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double,
        minZoom: Int,
        maxZoom: Int
    ) {
        val bounds = TileMath.boundsAroundPoint(centerLat, centerLon, radiusKm)
        val layer = preferences.value.mapLayer
        tileRepository.startRegionDownload(
            name = name.ifBlank { "Sector ${String.format(java.util.Locale.US, "%.2f, %.2f", centerLat, centerLon)}" },
            centerLat = centerLat,
            centerLon = centerLon,
            bounds = bounds,
            minZoom = minZoom,
            maxZoom = maxZoom,
            layer = layer
        )
    }

    fun cancelDownload() {
        tileRepository.cancelRegionDownload()
    }

    fun deleteRegion(regionId: Long) {
        viewModelScope.launch { tileRepository.deleteRegion(regionId) }
    }

    fun clearAllTileCache() {
        viewModelScope.launch { tileRepository.clearAllTileCache() }
    }

    // Waypoints
    fun addWaypoint(
        name: String,
        category: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double?,
        notes: String
    ) {
        viewModelScope.launch {
            dao.insertWaypoint(
                WaypointEntity(
                    name = name.trim().ifEmpty { "WP-${System.currentTimeMillis() % 10000}" },
                    category = category,
                    latitude = latitude,
                    longitude = longitude,
                    altitudeMeters = altitudeMeters,
                    notes = notes.trim()
                )
            )
        }
    }

    fun deleteWaypoint(id: Long) {
        viewModelScope.launch { dao.deleteWaypointById(id) }
    }

    // Field Intel
    fun queryFieldIntel(query: String) {
        val cleanQuery = query.trim().ifEmpty { "Nearby water sources, shelters, and trailheads" }
        val snap = telemetry.value
        val lat = snap.latitude ?: _mapViewport.value.centerLat
        val lon = snap.longitude ?: _mapViewport.value.centerLon

        viewModelScope.launch {
            _intelState.value = IntelUiState.Loading
            intelRepository.queryAreaIntel(lat, lon, cleanQuery)
                .onSuccess { report ->
                    _intelState.value = IntelUiState.Ready(report)
                    val highlights = report.places.joinToString("\n") {
                        "• ${it.name} (${it.estimatedRange}) — ${it.summary}"
                    }
                    dao.insertFieldIntel(
                        FieldIntelEntity(
                            query = report.query,
                            latitude = report.latitude,
                            longitude = report.longitude,
                            summary = report.overview,
                            highlights = highlights,
                            isPlaceholder = report.isPlaceholder
                        )
                    )
                }
                .onFailure { err ->
                    _intelState.value = IntelUiState.Error(err.localizedMessage ?: "Failed to query field intel")
                }
        }
    }

    fun deleteSavedIntel(id: Long) {
        viewModelScope.launch { dao.deleteFieldIntel(id) }
    }

    // Settings
    fun setSpeedUnit(unit: SpeedUnit) {
        viewModelScope.launch { settingsStore.setSpeedUnit(unit) }
    }

    fun setCoordinateFormat(format: CoordinateFormat) {
        viewModelScope.launch { settingsStore.setCoordinateFormat(format) }
    }

    fun setMapLayer(layer: MapLayer) {
        viewModelScope.launch { settingsStore.setMapLayer(layer) }
    }

    fun setOfflineOnlyMode(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setOfflineOnlyMode(enabled) }
    }

    fun setDarkHud(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDarkHud(enabled) }
    }

    override fun onCleared() {
        super.onCleared()
        telemetryManager.stopAll()
    }
}
