package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CoordinateFormat
import com.example.data.local.SpeedUnit
import com.example.data.local.UserPreferences
import com.example.data.location.CoordinateFormatter
import com.example.data.location.GpsFixState
import com.example.data.location.TelemetrySnapshot
import com.example.ui.TripMetrics
import com.example.ui.components.AddWaypointDialog
import com.example.ui.components.LinkAndFixStatusBar
import com.example.ui.components.MiniCompassDial
import com.example.ui.components.SpeedometerArcGauge
import com.example.ui.components.TelemetryMetricCard
import com.example.ui.theme.LocalTerraGridAccents
import com.example.ui.theme.TelemetryMonoLarge
import com.example.ui.theme.TelemetryMonoSmall
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TelemetryScreen(
    telemetry: TelemetrySnapshot,
    preferences: UserPreferences,
    tripMetrics: TripMetrics,
    onRequestLocationPermission: () -> Unit,
    onSelectSpeedUnit: (SpeedUnit) -> Unit,
    onSelectCoordinateFormat: (CoordinateFormat) -> Unit,
    onToggleTripRecording: () -> Unit,
    onResetTrip: () -> Unit,
    onAddWaypoint: (name: String, category: String, lat: Double, lon: Double, alt: Double?, notes: String) -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accents = LocalTerraGridAccents.current
    var showWaypointDialog by remember { mutableStateOf(false) }

    val lat = telemetry.latitude
    val lon = telemetry.longitude

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LinkAndFixStatusBar(
                    fixState = telemetry.fixState,
                    isNetworkOnline = telemetry.isNetworkOnline,
                    offlineOnlyMode = preferences.offlineOnlyMode,
                    satellitesUsed = telemetry.satellitesUsed,
                    satellitesVisible = telemetry.satellitesVisible
                )

                if (!telemetry.hasLocationPermission) {
                    PermissionPromptCard(
                        onRequestPermission = onRequestLocationPermission
                    )
                }
            }
        }

        // Hero Speedometer Card
        item {
            Surface(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SpeedometerArcGauge(
                        speedMps = telemetry.speedMps,
                        speedUnit = preferences.speedUnit,
                        fixState = telemetry.fixState
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Speed Unit Switcher Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        SpeedUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = preferences.speedUnit == unit,
                                onClick = { onSelectSpeedUnit(unit) },
                                label = {
                                    Text(
                                        text = unit.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                modifier = Modifier.testTag("speed_unit_${unit.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Precision GPS Coordinates Card
        item {
            Surface(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = "GPS Coordinates",
                                tint = accents.contourMint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PRECISION COORDINATES",
                                style = MaterialTheme.typography.labelMedium,
                                color = accents.contourMint
                            )
                        }
                        if (lat != null && lon != null) {
                            IconButton(
                                onClick = {
                                    val formatted = CoordinateFormatter.formatSingleLine(
                                        lat,
                                        lon,
                                        preferences.coordinateFormat
                                    )
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("GPS Coordinates", formatted))
                                    Toast.makeText(context, "Copied: $formatted", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("copy_coordinates_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy GPS coordinates",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (lat != null && lon != null) {
                        val (latText, lonText) = CoordinateFormatter.formatCoordinate(
                            lat,
                            lon,
                            preferences.coordinateFormat
                        )
                        val altFormat = if (preferences.coordinateFormat == CoordinateFormat.DMS) {
                            CoordinateFormat.DD
                        } else {
                            CoordinateFormat.DMS
                        }
                        val secondaryText = CoordinateFormatter.formatSingleLine(lat, lon, altFormat)
                        val maidenhead = CoordinateFormatter.toMaidenhead(lat, lon)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "LAT  $latText",
                                style = TelemetryMonoLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "LON  $lonText",
                                style = TelemetryMonoLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${altFormat.shortLabel}: $secondaryText",
                                    style = TelemetryMonoSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "GRID $maidenhead",
                                    style = TelemetryMonoSmall,
                                    color = accents.signalOrange
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (telemetry.fixState == GpsFixState.PERMISSION_REQUIRED) {
                                "Grant location access to lock onto GNSS satellites."
                            } else {
                                "Acquiring GNSS satellite lock… Ensure clear sky view for accurate coordinates."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Coordinate Format Chips + Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CoordinateFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = preferences.coordinateFormat == fmt,
                                onClick = { onSelectCoordinateFormat(fmt) },
                                label = { Text(fmt.shortLabel, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("coord_format_${fmt.name.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { if (lat != null && lon != null) showWaypointDialog = true },
                            enabled = lat != null && lon != null,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("mark_waypoint_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddLocationAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mark Waypoint")
                        }
                        OutlinedButton(
                            onClick = onOpenMap,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_topo_map_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Terrain,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Topo Map")
                        }
                    }
                }
            }
        }

        // Compass + Accuracy + Elevation Telemetry Grid
        item {
            Column(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val accText = telemetry.accuracyMeters?.let { "±${it.roundToInt()} m" } ?: "--"
                    val accSub = when {
                        telemetry.accuracyMeters == null -> "Awaiting fix"
                        telemetry.accuracyMeters <= 8f -> "High Precision"
                        telemetry.accuracyMeters <= 25f -> "Standard Fix"
                        else -> "Coarse Signal"
                    }
                    TelemetryMetricCard(
                        label = "GPS Accuracy",
                        value = accText,
                        subtext = "${telemetry.provider} • $accSub",
                        accentColor = accents.contourMint,
                        modifier = Modifier.weight(1f)
                    )

                    val altText = telemetry.altitudeMeters?.let { "${it.roundToInt()} m" } ?: "--"
                    val altSub = telemetry.barometerHpa?.let {
                        String.format(Locale.US, "%.1f hPa Baro", it)
                    } ?: telemetry.altitudeMeters?.let {
                        String.format(Locale.US, "%.0f ft ASL", it * 3.28084)
                    } ?: "GNSS Ellipsoid"
                    TelemetryMetricCard(
                        label = "Elevation",
                        value = altText,
                        subtext = altSub,
                        accentColor = accents.signalOrange,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Heading / Compass Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "COMPASS BEARING & TRACK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val heading = telemetry.gpsBearingDegrees ?: telemetry.compassAzimuthDegrees
                            val cardinal = CoordinateFormatter.cardinalDirection(heading)
                            Text(
                                text = "${heading.roundToInt()}° $cardinal",
                                style = TelemetryMonoLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Mag Azimuth: ${telemetry.compassAzimuthDegrees.roundToInt()}°" +
                                    (telemetry.gpsBearingDegrees?.let { " • COG: ${it.roundToInt()}°" } ?: ""),
                                style = TelemetryMonoSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        MiniCompassDial(azimuthDegrees = telemetry.compassAzimuthDegrees)
                    }
                }
            }
        }

        // Trip Computer & Breadcrumb Recorder
        item {
            Surface(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "EXPEDITION TRIP LOG",
                                style = MaterialTheme.typography.labelMedium,
                                color = accents.signalOrange
                            )
                            Text(
                                text = if (tripMetrics.isRecording) {
                                    "Recording GNSS track offline (${tripMetrics.recordedPointsCount} pts)"
                                } else {
                                    "Record distance, peak speed & map breadcrumb"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryMetricCard(
                            label = "Distance",
                            value = CoordinateFormatter.formatDistance(tripMetrics.distanceMeters),
                            subtext = "Time ${CoordinateFormatter.formatDuration(tripMetrics.elapsedMs)}",
                            accentColor = accents.contourMint,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricCard(
                            label = "Peak / Avg",
                            value = "${CoordinateFormatter.formatSpeed(tripMetrics.maxSpeedMps, preferences.speedUnit)} ${preferences.speedUnit.label}",
                            subtext = "Avg ${CoordinateFormatter.formatSpeed(tripMetrics.avgSpeedMps, preferences.speedUnit)} ${preferences.speedUnit.label}",
                            accentColor = accents.cautionAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onToggleTripRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tripMetrics.isRecording) accents.cautionAmber else accents.contourMint,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trip_record_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (tripMetrics.isRecording) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (tripMetrics.isRecording) "Pause Log" else "Start Track Log")
                        }
                        OutlinedButton(
                            onClick = onResetTrip,
                            modifier = Modifier.testTag("trip_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reset trip log",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }

    if (showWaypointDialog && lat != null && lon != null) {
        AddWaypointDialog(
            latitude = lat,
            longitude = lon,
            altitudeMeters = telemetry.altitudeMeters,
            onDismiss = { showWaypointDialog = false },
            onSave = { name, category, notes ->
                onAddWaypoint(name, category, lat, lon, telemetry.altitudeMeters, notes)
                showWaypointDialog = false
                Toast.makeText(context, "Waypoint cached offline", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun PermissionPromptCard(onRequestPermission: () -> Unit) {
    val accents = LocalTerraGridAccents.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = accents.signalOrange.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accents.signalOrange.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable Precision GNSS Access",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Required for live GPS speed, altitude, and accurate coordinates off-grid.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = accents.signalOrange),
                modifier = Modifier.testTag("grant_location_permission_button")
            ) {
                Text("Enable")
            }
        }
    }
}
