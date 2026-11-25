package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CoordinateFormat
import com.example.data.local.OfflineRegionEntity
import com.example.data.local.TileCacheStats
import com.example.data.local.UserPreferences
import com.example.data.local.WaypointEntity
import com.example.data.location.CoordinateFormatter
import com.example.data.location.TelemetrySnapshot
import com.example.data.maps.RegionDownloadProgress
import com.example.data.maps.TileMath
import com.example.ui.MapViewportState
import com.example.ui.RemoteAreaPreset
import com.example.ui.theme.LocalTerraGridAccents
import com.example.ui.theme.TelemetryMonoSmall
import kotlin.math.roundToInt

@Composable
fun OfflineCacheScreen(
    telemetry: TelemetrySnapshot,
    preferences: UserPreferences,
    viewport: MapViewportState,
    tileStats: TileCacheStats,
    regions: List<OfflineRegionEntity>,
    waypoints: List<WaypointEntity>,
    presets: List<RemoteAreaPreset>,
    downloadProgress: RegionDownloadProgress?,
    onDownloadRegion: (name: String, lat: Double, lon: Double, radiusKm: Double, minZ: Int, maxZ: Int) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteRegion: (Long) -> Unit,
    onClearAllCache: () -> Unit,
    onJumpToMap: (Double, Double, Int) -> Unit,
    onDeleteWaypoint: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accents = LocalTerraGridAccents.current
    var regionName by remember { mutableStateOf("") }
    var selectedRadiusKm by remember { mutableDoubleStateOf(3.0) }
    var selectedMaxZoom by remember { mutableIntStateOf(14) }

    val targetLat = telemetry.latitude ?: viewport.centerLat
    val targetLon = telemetry.longitude ?: viewport.centerLon
    val bounds = remember(targetLat, targetLon, selectedRadiusKm) {
        TileMath.boundsAroundPoint(targetLat, targetLon, selectedRadiusKm)
    }
    val estTiles = remember(bounds, selectedMaxZoom) {
        TileMath.estimateTileCount(bounds, 10, selectedMaxZoom)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Topographic Cache Banner
        item {
            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.height(152.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_topo_banner_1795615108143),
                        contentDescription = "Offline topographic cache header",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x660A1011),
                                        Color(0xEB0A1011)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "OFFLINE MAP VAULT",
                            style = MaterialTheme.typography.labelMedium,
                            color = accents.contourMint
                        )
                        Text(
                            text = "${tileStats.tileCount} Cached Tiles • ${CoordinateFormatter.formatBytes(tileStats.totalBytes)}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${regions.size} saved remote regions • Layer: ${preferences.mapLayer.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCAD8D5)
                        )
                    }
                }
            }
        }

        // Active Download Progress Card
        if (downloadProgress != null && downloadProgress.isActive) {
            item {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = accents.contourMint.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accents.contourMint)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Downloading: ${downloadProgress.regionName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = onCancelDownload,
                                modifier = Modifier.testTag("cancel_region_download_button")
                            ) {
                                Icon(Icons.Filled.Cancel, contentDescription = "Cancel download", tint = accents.alertRed)
                            }
                        }
                        val progress = if (downloadProgress.total > 0) {
                            downloadProgress.downloaded.toFloat() / downloadProgress.total.toFloat()
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = accents.contourMint
                        )
                        Text(
                            text = "${downloadProgress.downloaded} / ${downloadProgress.total} tiles • ${CoordinateFormatter.formatBytes(downloadProgress.bytesSaved)}",
                            style = TelemetryMonoSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Cache Current Coordinates / Custom Area Builder
        item {
            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CACHE REMOTE LOCATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = accents.signalOrange
                    )
                    Text(
                        text = "Target: ${CoordinateFormatter.formatSingleLine(targetLat, targetLon, CoordinateFormat.DD)}",
                        style = TelemetryMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = regionName,
                        onValueChange = { regionName = it },
                        label = { Text("Expedition Sector Name") },
                        placeholder = { Text("e.g. Basecamp Sector A") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("region_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2.0 to "2 km", 3.0 to "3 km", 5.0 to "5 km", 10.0 to "10 km").forEach { (r, label) ->
                            FilterChip(
                                selected = selectedRadiusKm == r,
                                onClick = { selectedRadiusKm = r },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(13 to "Z10-13", 14 to "Z10-14", 15 to "Z10-15").forEach { (z, label) ->
                            FilterChip(
                                selected = selectedMaxZoom == z,
                                onClick = { selectedMaxZoom = z },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$estTiles tiles (~${String.format(java.util.Locale.US, "%.1f", estTiles * 22.0 / 1024.0)} MB)",
                            style = TelemetryMonoSmall,
                            color = accents.contourMint
                        )
                        Button(
                            onClick = {
                                onDownloadRegion(
                                    regionName,
                                    targetLat,
                                    targetLon,
                                    selectedRadiusKm,
                                    10,
                                    selectedMaxZoom
                                )
                                regionName = ""
                            },
                            enabled = telemetry.isNetworkOnline && !preferences.offlineOnlyMode && downloadProgress == null,
                            modifier = Modifier.testTag("download_current_region_button")
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download Pack")
                        }
                    }
                    if (!telemetry.isNetworkOnline || preferences.offlineOnlyMode) {
                        Text(
                            text = "Connect to network (and disable Off-Grid Mode) to download new map packs. Previously cached regions remain available offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.cautionAmber
                        )
                    }
                }
            }
        }

        // Remote Expedition Presets
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "REMOTE BACKCOUNTRY PRESETS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                presets.forEach { preset ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${preset.subtitle} • ${String.format(java.util.Locale.US, "%.3f, %.3f", preset.lat, preset.lon)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onJumpToMap(preset.lat, preset.lon, 13) }
                                ) {
                                    Text("View")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onDownloadRegion(preset.name, preset.lat, preset.lon, 3.0, 10, 14)
                                    },
                                    enabled = telemetry.isNetworkOnline && !preferences.offlineOnlyMode && downloadProgress == null
                                ) {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = "Cache ${preset.name}", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Saved Offline Regions
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CACHED MAP REGIONS (${regions.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (tileStats.tileCount > 0) {
                    OutlinedButton(
                        onClick = onClearAllCache,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accents.alertRed),
                        modifier = Modifier.testTag("clear_tile_cache_button")
                    ) {
                        Text("Clear Cache")
                    }
                }
            }
        }

        if (regions.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FolderZip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "No offline map packs saved yet. Download your current area or pan the map to auto-cache tiles before heading off-grid.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(regions, key = { it.id }) { region ->
                Surface(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = region.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${region.status} • ${region.downloadedTiles}/${region.totalTiles} tiles • ${CoordinateFormatter.formatBytes(region.totalBytes)}",
                                style = TelemetryMonoSmall,
                                color = accents.contourMint
                            )
                            Text(
                                text = "Z${region.minZoom}–Z${region.maxZoom} • ${region.layerId.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onJumpToMap(region.centerLat, region.centerLon, region.minZoom + 2) }
                            ) {
                                Icon(Icons.Filled.Explore, contentDescription = "Open region on map", tint = accents.contourMint)
                            }
                            IconButton(
                                onClick = { onDeleteRegion(region.id) }
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete region", tint = accents.alertRed)
                            }
                        }
                    }
                }
            }
        }

        // Saved Offline Waypoints
        item {
            Text(
                text = "OFFLINE WAYPOINTS (${waypoints.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
            )
        }

        if (waypoints.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "No saved waypoints yet. Tap \"Mark Waypoint\" on the Telemetry HUD or Map screen to store coordinates offline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(waypoints, key = { it.id }) { wp ->
                val distText = if (telemetry.latitude != null && telemetry.longitude != null) {
                    val d = CoordinateFormatter.haversineMeters(
                        telemetry.latitude,
                        telemetry.longitude,
                        wp.latitude,
                        wp.longitude
                    )
                    val b = CoordinateFormatter.bearingDegrees(
                        telemetry.latitude,
                        telemetry.longitude,
                        wp.latitude,
                        wp.longitude
                    )
                    "${CoordinateFormatter.formatDistance(d)} • ${b.roundToInt()}° ${CoordinateFormatter.cardinalDirection(b)}"
                } else {
                    CoordinateFormatter.formatSingleLine(wp.latitude, wp.longitude, preferences.coordinateFormat)
                }

                Surface(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "[${wp.category}] ${wp.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = distText,
                                style = TelemetryMonoSmall,
                                color = accents.signalOrange
                            )
                            if (wp.notes.isNotBlank()) {
                                Text(
                                    text = wp.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row {
                            IconButton(onClick = { onJumpToMap(wp.latitude, wp.longitude, 14) }) {
                                Icon(Icons.Filled.Explore, contentDescription = "Show waypoint on map", tint = accents.contourMint)
                            }
                            IconButton(onClick = { onDeleteWaypoint(wp.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete waypoint", tint = accents.alertRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
