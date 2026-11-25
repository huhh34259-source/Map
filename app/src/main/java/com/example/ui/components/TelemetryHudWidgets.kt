package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.SpeedUnit
import com.example.data.location.CoordinateFormatter
import com.example.data.location.GpsFixState
import com.example.ui.theme.LocalTerraGridAccents
import com.example.ui.theme.TelemetryDisplayStyle
import com.example.ui.theme.TelemetryMonoLarge
import com.example.ui.theme.TelemetryMonoSmall
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LinkAndFixStatusBar(
    fixState: GpsFixState,
    isNetworkOnline: Boolean,
    offlineOnlyMode: Boolean,
    satellitesUsed: Int,
    satellitesVisible: Int,
    modifier: Modifier = Modifier
) {
    val accents = LocalTerraGridAccents.current
    val (fixColor, fixIcon, fixLabel) = when (fixState) {
        GpsFixState.LOCKED_3D -> Triple(accents.contourMint, Icons.Filled.GpsFixed, "3D GNSS LOCK")
        GpsFixState.ACQUIRING -> Triple(accents.cautionAmber, Icons.Filled.GpsNotFixed, "ACQUIRING FIX")
        GpsFixState.SEARCHING -> Triple(accents.signalOrange, Icons.Filled.GpsNotFixed, "SEARCHING SAT")
        GpsFixState.PERMISSION_REQUIRED -> Triple(accents.alertRed, Icons.Filled.GpsOff, "GPS PERMISSION")
    }

    val effectiveOffline = offlineOnlyMode || !isNetworkOnline
    val linkColor = if (effectiveOffline) accents.cautionAmber else accents.contourMint
    val linkIcon = if (effectiveOffline) Icons.Filled.CloudOff else Icons.Filled.Wifi
    val linkText = when {
        offlineOnlyMode -> "OFF-GRID MODE"
        isNetworkOnline -> "ONLINE CACHE"
        else -> "OFF-GRID (CACHED)"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusBadgePill(
            color = fixColor,
            icon = fixIcon,
            label = if (satellitesVisible > 0) "$fixLabel • $satellitesUsed/$satellitesVisible" else fixLabel,
            modifier = Modifier.weight(1f)
        )
        StatusBadgePill(
            color = linkColor,
            icon = linkIcon,
            label = linkText
        )
    }
}

@Composable
fun StatusBadgePill(
    color: Color,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SpeedometerArcGauge(
    speedMps: Float,
    speedUnit: SpeedUnit,
    fixState: GpsFixState,
    modifier: Modifier = Modifier
) {
    val accents = LocalTerraGridAccents.current
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outline

    val convertedSpeed = (speedMps * speedUnit.factorFromMps).toFloat()
    val maxScale = when (speedUnit) {
        SpeedUnit.KMH -> 120f
        SpeedUnit.MPH -> 80f
        SpeedUnit.KTS -> 65f
        SpeedUnit.MS -> 35f
    }
    val targetFraction = (convertedSpeed / maxScale).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 240f),
        label = "speed_arc"
    )

    val formattedSpeed = CoordinateFormatter.formatSpeed(speedMps, speedUnit)

    Box(
        modifier = modifier
            .size(238.dp)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 15.dp.toPx()
            val inset = strokePx / 2f + 10.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val startAngle = 140f
            val sweepTotal = 260f

            // Subtle tick marks around outer perimeter
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerR = size.minDimension / 2f - 2.dp.toPx()
            for (i in 0..20) {
                val frac = i / 20f
                val angleDeg = startAngle + sweepTotal * frac
                val rad = Math.toRadians(angleDeg.toDouble())
                val innerR = if (i % 5 == 0) outerR - 8.dp.toPx() else outerR - 4.dp.toPx()
                drawLine(
                    color = if (frac <= animatedFraction && animatedFraction > 0.01f) {
                        accents.signalOrange
                    } else {
                        outline.copy(alpha = 0.5f)
                    },
                    start = Offset(
                        center.x + (cos(rad) * innerR).toFloat(),
                        center.y + (sin(rad) * innerR).toFloat()
                    ),
                    end = Offset(
                        center.x + (cos(rad) * outerR).toFloat(),
                        center.y + (sin(rad) * outerR).toFloat()
                    ),
                    strokeWidth = if (i % 5 == 0) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Track arc
            drawArc(
                color = surfaceVariant,
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active speed arc
            if (animatedFraction > 0.005f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(accents.contourMint, accents.cautionAmber, accents.signalOrange)
                    ),
                    startAngle = startAngle,
                    sweepAngle = sweepTotal * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (fixState == GpsFixState.PERMISSION_REQUIRED) "--.-" else formattedSpeed,
                style = TelemetryDisplayStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = speedUnit.label.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = accents.contourMint,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "GPS GROUND SPEED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MiniCompassDial(
    azimuthDegrees: Float,
    modifier: Modifier = Modifier
) {
    val accents = LocalTerraGridAccents.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f - 3.dp.toPx()
            drawCircle(
                color = outline.copy(alpha = 0.55f),
                radius = r,
                style = Stroke(width = 1.5.dp.toPx())
            )
            rotate(-azimuthDegrees) {
                // North pointer (Signal Orange)
                drawLine(
                    color = accents.signalOrange,
                    start = center,
                    end = Offset(center.x, center.y - r + 6.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // South pointer
                drawLine(
                    color = onSurface.copy(alpha = 0.4f),
                    start = center,
                    end = Offset(center.x, center.y + r - 8.dp.toPx()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(color = accents.contourMint, radius = 3.dp.toPx())
        }
    }
}

@Composable
fun TelemetryMetricCard(
    label: String,
    value: String,
    subtext: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = TelemetryMonoLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtext != null) {
                Text(
                    text = subtext,
                    style = TelemetryMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AddWaypointDialog(
    latitude: Double,
    longitude: Double,
    altitudeMeters: Double?,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, notes: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val categories = listOf("CAMP", "WATER", "SHELTER", "TRAILHEAD", "SUMMIT", "HAZARD")
    var selectedCategory by remember { mutableStateOf("CAMP") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Offline Waypoint") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = String.format(
                        java.util.Locale.US,
                        "%.6f°, %.6f°",
                        latitude,
                        longitude
                    ) + (altitudeMeters?.let { " • ${it.toInt()} m" } ?: ""),
                    style = TelemetryMonoSmall,
                    color = LocalTerraGridAccents.current.contourMint
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Waypoint Name") },
                    placeholder = { Text("e.g. Ridge Spring #1") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("waypoint_name_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.drop(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Field Notes (optional)") },
                    placeholder = { Text("Water flow, terrain, shelter notes...") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, selectedCategory, notes) },
                modifier = Modifier.testTag("confirm_save_waypoint_button")
            ) {
                Text("Save Offline")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
