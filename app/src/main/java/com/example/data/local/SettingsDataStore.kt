package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "terragrid_prefs")

enum class SpeedUnit(val label: String, val factorFromMps: Double) {
    KMH("km/h", 3.6),
    MPH("mph", 2.23693629),
    KTS("kts", 1.94384449),
    MS("m/s", 1.0)
}

enum class CoordinateFormat(val label: String, val shortLabel: String) {
    DD("Decimal Degrees", "DD"),
    DMS("Deg Min Sec", "DMS"),
    DDM("Deg Dec Min", "DDM")
}

enum class MapLayer(val id: String, val label: String) {
    TOPO("topo", "OpenTopoMap"),
    STREET("osm", "OpenStreetMap")
}

data class UserPreferences(
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DD,
    val mapLayer: MapLayer = MapLayer.TOPO,
    val offlineOnlyMode: Boolean = false,
    val darkHud: Boolean = true
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val COORD_FORMAT = stringPreferencesKey("coord_format")
        val MAP_LAYER = stringPreferencesKey("map_layer")
        val OFFLINE_ONLY = booleanPreferencesKey("offline_only")
        val DARK_HUD = booleanPreferencesKey("dark_hud")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            speedUnit = prefs[Keys.SPEED_UNIT]?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }
                ?: SpeedUnit.KMH,
            coordinateFormat = prefs[Keys.COORD_FORMAT]?.let {
                runCatching { CoordinateFormat.valueOf(it) }.getOrNull()
            } ?: CoordinateFormat.DD,
            mapLayer = prefs[Keys.MAP_LAYER]?.let { runCatching { MapLayer.valueOf(it) }.getOrNull() }
                ?: MapLayer.TOPO,
            offlineOnlyMode = prefs[Keys.OFFLINE_ONLY] ?: false,
            darkHud = prefs[Keys.DARK_HUD] ?: true
        )
    }

    suspend fun setSpeedUnit(unit: SpeedUnit) {
        context.dataStore.edit { it[Keys.SPEED_UNIT] = unit.name }
    }

    suspend fun setCoordinateFormat(format: CoordinateFormat) {
        context.dataStore.edit { it[Keys.COORD_FORMAT] = format.name }
    }

    suspend fun setMapLayer(layer: MapLayer) {
        context.dataStore.edit { it[Keys.MAP_LAYER] = layer.name }
    }

    suspend fun setOfflineOnlyMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OFFLINE_ONLY] = enabled }
    }

    suspend fun setDarkHud(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_HUD] = enabled }
    }
}
