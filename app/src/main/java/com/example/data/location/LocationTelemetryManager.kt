package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs

enum class GpsFixState {
    PERMISSION_REQUIRED,
    SEARCHING,
    ACQUIRING,
    LOCKED_3D
}

data class TelemetrySnapshot(
    val hasLocationPermission: Boolean = false,
    val fixState: GpsFixState = GpsFixState.PERMISSION_REQUIRED,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val speedMps: Float = 0f,
    val accuracyMeters: Float? = null,
    val gpsBearingDegrees: Float? = null,
    val compassAzimuthDegrees: Float = 0f,
    val barometerHpa: Float? = null,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val provider: String = "GNSS",
    val lastFixTimestampMs: Long? = null,
    val isNetworkOnline: Boolean = false
)

class LocationTelemetryManager(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _telemetry = MutableStateFlow(
        TelemetrySnapshot(isNetworkOnline = checkCurrentlyOnline())
    )
    val telemetry: StateFlow<TelemetrySnapshot> = _telemetry.asStateFlow()

    private var isLocationStarted = false
    private var isSensorsStarted = false
    private var isNetworkRegistered = false

    private val gravityReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetometer = false
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var lastLocationForSpeedFallback: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { processLocation(it, "Fused GNSS") }
        }
    }

    private val gpsFallbackListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            processLocation(location, location.provider?.uppercase() ?: "GPS")
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            val total = status.satelliteCount
            for (i in 0 until total) {
                if (status.usedInFix(i)) used++
            }
            _telemetry.update {
                it.copy(satellitesUsed = used, satellitesVisible = total)
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val azimuth = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
                    smoothAzimuthUpdate(azimuth)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, gravityReading, 0, 3)
                    hasGravity = true
                    computeFallbackAzimuth()
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
                    hasMagnetometer = true
                    computeFallbackAzimuth()
                }
                Sensor.TYPE_PRESSURE -> {
                    val hpa = event.values.firstOrNull() ?: return
                    _telemetry.update { it.copy(barometerHpa = hpa) }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _telemetry.update { it.copy(isNetworkOnline = checkCurrentlyOnline()) }
        }

        override fun onLost(network: Network) {
            _telemetry.update { it.copy(isNetworkOnline = checkCurrentlyOnline()) }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _telemetry.update { it.copy(isNetworkOnline = validated) }
        }
    }

    private fun computeFallbackAzimuth() {
        if (!hasGravity || !hasMagnetometer) return
        if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityReading, magnetometerReading)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuth = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
            smoothAzimuthUpdate(azimuth)
        }
    }

    private fun smoothAzimuthUpdate(rawAzimuth: Float) {
        _telemetry.update { current ->
            val prev = current.compassAzimuthDegrees
            var delta = rawAzimuth - prev
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            if (abs(delta) < 0.8f) return@update current
            val smoothed = ((prev + delta * 0.25f) + 360f) % 360f
            current.copy(compassAzimuthDegrees = smoothed)
        }
    }

    private fun processLocation(location: Location, providerLabel: String) {
        val prev = lastLocationForSpeedFallback
        val rawSpeed = when {
            location.hasSpeed() -> location.speed
            prev != null && location.time > prev.time -> {
                val dt = (location.time - prev.time) / 1000.0
                val dist = prev.distanceTo(location).toDouble()
                if (dt > 0.5 && dist > 1.5) (dist / dt).toFloat() else 0f
            }
            else -> 0f
        }
        // Filter stationary GNSS micro-drift below 0.35 m/s (~1.2 km/h)
        val filteredSpeed = if (rawSpeed < 0.35f) 0f else rawSpeed
        lastLocationForSpeedFallback = location

        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val fixState = when {
            accuracy != null && accuracy <= 25f -> GpsFixState.LOCKED_3D
            else -> GpsFixState.ACQUIRING
        }

        _telemetry.update { current ->
            current.copy(
                hasLocationPermission = true,
                fixState = fixState,
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = if (location.hasAltitude()) location.altitude else current.altitudeMeters,
                speedMps = filteredSpeed,
                accuracyMeters = accuracy,
                gpsBearingDegrees = if (location.hasBearing()) location.bearing else current.gpsBearingDegrees,
                provider = providerLabel,
                lastFixTimestampMs = System.currentTimeMillis()
            )
        }
    }

    fun startPassiveSensorsAndNetwork() {
        if (!isNetworkRegistered) {
            runCatching {
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(req, networkCallback)
                isNetworkRegistered = true
            }
        }
        if (!isSensorsStarted) {
            val rotSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotSensor != null) {
                sensorManager.registerListener(sensorListener, rotSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                    sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
                }
                sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
                    sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI)
                }
            }
            sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
                sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            isSensorsStarted = true
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        _telemetry.update {
            it.copy(
                hasLocationPermission = true,
                fixState = if (it.latitude == null) GpsFixState.SEARCHING else it.fixState
            )
        }
        if (isLocationStarted) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(false)
            .build()

        runCatching {
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && _telemetry.value.latitude == null) {
                    processLocation(loc, "Last GNSS Fix")
                }
            }
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        }

        runCatching {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    gpsFallbackListener,
                    Looper.getMainLooper()
                )
            }
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.registerGnssStatusCallback(context.mainExecutor, gnssCallback)
            } else {
                @Suppress("DEPRECATION")
                locationManager.registerGnssStatusCallback(gnssCallback)
            }
        }

        isLocationStarted = true
    }

    fun onPermissionDenied() {
        _telemetry.update {
            it.copy(
                hasLocationPermission = false,
                fixState = GpsFixState.PERMISSION_REQUIRED
            )
        }
    }

    fun stopAll() {
        if (isLocationStarted) {
            runCatching { fusedClient.removeLocationUpdates(locationCallback) }
            runCatching { locationManager.removeUpdates(gpsFallbackListener) }
            runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
            isLocationStarted = false
        }
        if (isSensorsStarted) {
            runCatching { sensorManager.unregisterListener(sensorListener) }
            isSensorsStarted = false
        }
        if (isNetworkRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            isNetworkRegistered = false
        }
    }

    private fun checkCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
