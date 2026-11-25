package com.example.data.location

import com.example.data.local.CoordinateFormat
import com.example.data.local.SpeedUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object CoordinateFormatter {

    fun formatCoordinate(
        lat: Double,
        lon: Double,
        format: CoordinateFormat
    ): Pair<String, String> = when (format) {
        CoordinateFormat.DD -> formatDd(lat, lon)
        CoordinateFormat.DMS -> formatDms(lat, lon)
        CoordinateFormat.DDM -> formatDdm(lat, lon)
    }

    fun formatSingleLine(lat: Double, lon: Double, format: CoordinateFormat): String {
        val (latStr, lonStr) = formatCoordinate(lat, lon, format)
        return "$latStr  $lonStr"
    }

    private fun formatDd(lat: Double, lon: Double): Pair<String, String> {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        val latStr = String.format(Locale.US, "%.6f° %s", abs(lat), latDir)
        val lonStr = String.format(Locale.US, "%.6f° %s", abs(lon), lonDir)
        return latStr to lonStr
    }

    private fun formatDms(lat: Double, lon: Double): Pair<String, String> {
        return toDms(lat, isLat = true) to toDms(lon, isLat = false)
    }

    private fun toDms(value: Double, isLat: Boolean): String {
        val dir = if (isLat) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "E" else "W"
        }
        val absVal = abs(value)
        val deg = floor(absVal).toInt()
        val minFloat = (absVal - deg) * 60.0
        val min = floor(minFloat).toInt()
        val sec = (minFloat - min) * 60.0
        return String.format(Locale.US, "%d°%02d'%05.2f\"%s", deg, min, sec, dir)
    }

    private fun formatDdm(lat: Double, lon: Double): Pair<String, String> {
        return toDdm(lat, isLat = true) to toDdm(lon, isLat = false)
    }

    private fun toDdm(value: Double, isLat: Boolean): String {
        val dir = if (isLat) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "E" else "W"
        }
        val absVal = abs(value)
        val deg = floor(absVal).toInt()
        val min = (absVal - deg) * 60.0
        return String.format(Locale.US, "%d°%06.3f'%s", deg, min, dir)
    }

    fun toMaidenhead(lat: Double, lon: Double): String {
        val adjLon = (lon + 180.0).coerceIn(0.0, 359.999999)
        val adjLat = (lat + 90.0).coerceIn(0.0, 179.999999)

        val f1 = ('A'.code + (adjLon / 20.0).toInt()).toChar()
        val f2 = ('A'.code + (adjLat / 10.0).toInt()).toChar()

        val s1 = ((adjLon % 20.0) / 2.0).toInt()
        val s2 = (adjLat % 10.0).toInt()

        val sub1 = ('a'.code + (((adjLon % 2.0) / 2.0) * 24.0).toInt().coerceIn(0, 23)).toChar()
        val sub2 = ('a'.code + (((adjLat % 1.0)) * 24.0).toInt().coerceIn(0, 23)).toChar()

        return "$f1$f2$s1$s2$sub1$sub2"
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val theta = atan2(y, x)
        return ((theta * 180.0 / PI + 360.0) % 360.0).toFloat()
    }

    fun cardinalDirection(degrees: Float): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val normalized = ((degrees % 360f) + 360f) % 360f
        val index = ((normalized + 22.5f) / 45f).toInt() % 8
        return dirs[index]
    }

    fun formatSpeed(speedMps: Float, unit: SpeedUnit): String {
        val value = speedMps * unit.factorFromMps
        return if (value >= 100.0) {
            String.format(Locale.US, "%.0f", value)
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }

    fun formatDistance(meters: Double): String {
        return if (meters >= 1000.0) {
            String.format(Locale.US, "%.2f km", meters / 1000.0)
        } else {
            String.format(Locale.US, "%d m", meters.roundToInt())
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatDuration(elapsedMs: Long): String {
        val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
