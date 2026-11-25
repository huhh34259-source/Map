package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.local.MapLayer
import com.example.data.local.TrackPointEntity
import com.example.data.local.UserPreferences
import com.example.data.local.WaypointEntity
import com.example.data.location.CoordinateFormatter
import com.example.data.location.TelemetrySnapshot
import com.example.data.maps.RegionDownloadProgress
import com.example.data.maps.TileMath
import com.example.ui.MapViewportState
import com.example.ui.components.AddWaypointDialog
import com.example.ui.components.LinkAndFixStatusBar
import com.example.ui.theme.LocalTerraGridAccents
import com.example.ui.theme.TelemetryMonoLarge
import com.example.ui.theme.TelemetryMonoSmall
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun OfflineMapScreen(
    telemetry: TelemetrySnapshot,
    preferences: UserPreferences,
    viewport: MapViewportState,
    waypoints: List<WaypointEntity>,
    trackPoints: List<TrackPointEntity>,
    downloadProgress: RegionDownloadProgress?,
    loadTile: suspend (MapLayer, Int, Int, Int) -> ImageBitmap?,
    onPanMap: (Float, Float, Float) -> Unit,
    onSetZoom: (Int) -> Unit,
    onCenterOnGps: () -> Unit,
    onToggleLayer: (MapLayer) -> Unit,
    onAddWaypoint: (name: String, category: String, lat: Double, lon: Double, alt: Double?, notes: String) -> Unit,
    onDownloadViewportOffline: (name: String, centerLat: Double, centerLon: Double, radiusKm: Double, minZoom: Int, maxZoom: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accents = LocalTerraGridAccents.current
    val density = LocalDensity.current
    val tileDisplayPx = with(density) { 256.dp.toPx() }

    val loadedTiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    var pinchAccumulator by remember { mutableFloatStateOf(1f) }
    var showSaveAreaDialog by remember { mutableStateOf(false) }
    var showDropWaypointDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val zoom = viewport.zoom
        val worldTileCount = 1 shl zoom
        val centerTileX = TileMath.lonToTileXDouble(viewport.centerLon, zoom)
        val centerTileY = TileMath.latToTileYDouble(viewport.centerLat, zoom)

        val halfTilesX = ceil((widthPx / 2f) / tileDisplayPx).toInt() + 1
        val halfTilesY = ceil((heightPx / 2f) / tileDisplayPx).toInt() + 1

        val minTileX = floor(centerTileX).toInt() - halfTilesX
        val maxTileX = floor(centerTileX).toInt() + halfTilesX
        val minTileY = (floor(centerTileY).toInt() - halfTilesY).coerceIn(0, worldTileCount - 1)
        val maxTileY = (floor(centerTileY).toInt() + halfTilesY).coerceIn(0, worldTileCount - 1)

        // Fetch / read cached tiles for current viewport
        LaunchedEffect(
            minTileX,
            maxTileX,
            minTileY,
            maxTileY,
            zoom,
            preferences.mapLayer,
            preferences.offlineOnlyMode,
            telemetry.isNetworkOnline
        ) {
            for (ty in minTileY..maxTileY) {
                for (txRaw in minTileX..maxTileX) {
                    val tx = ((txRaw % worldTileCount) + worldTileCount) % worldTileCount
                    val key = "${preferences.mapLayer.id}/$zoom/$tx/$ty"
                    if (!loadedTiles.containsKey(key)) {
                        launch {
                            val bmp = loadTile(preferences.mapLayer, zoom, tx, ty)
                            if (bmp != null) {
                                loadedTiles[key] = bmp
                            }
                        }
                    }
                }
            }
        }

        // Interactive Slippy Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("offline_map_canvas")
                .pointerInput(zoom, tileDisplayPx) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        if (pan != Offset.Zero) {
                            onPanMap(pan.x, pan.y, tileDisplayPx)
                        }
                        if (gestureZoom != 1f) {
                            pinchAccumulator *= gestureZoom
                            if (pinchAccumulator > 1.32f) {
                                onSetZoom(zoom + 1)
                                pinchAccumulator = 1f
                            } else if (pinchAccumulator < 0.75f) {
                                onSetZoom(zoom - 1)
                                pinchAccumulator = 1f
                            }
                        }
                    }
                }
        ) {
            // Base off-grid topographic background
            drawRect(color = accents.topoCanvasBg)

            val screenCenterX = size.width / 2f
            val screenCenterY = size.height / 2f

            // Draw XYZ tiles (cached or live) + subtle tactical grid fallback for uncached tiles
            for (ty in minTileY..maxTileY) {
                for (txRaw in minTileX..maxTileX) {
                    val tx = ((txRaw % worldTileCount) + worldTileCount) % worldTileCount
                    val dx = screenCenterX + ((txRaw - centerTileX) * tileDisplayPx).toFloat()
                    val dy = screenCenterY + ((ty - centerTileY) * tileDisplayPx).toFloat()
                    val key = "${preferences.mapLayer.id}/$zoom/$tx/$ty"
                    val tileBitmap = loadedTiles[key]

                    if (tileBitmap != null) {
                        drawImage(
                            image = tileBitmap,
                            dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                            dstSize = IntSize(ceil(tileDisplayPx).toInt() + 1, ceil(tileDisplayPx).toInt() + 1)
                        )
                    } else {
                        // Topographic vector fallback grid when tile isn't cached offline
                        val subStep = tileDisplayPx / 4f
                        for (i in 0..3) {
                            val gx = dx + i * subStep
                            val gy = dy + i * subStep
                            drawLine(
                                color = accents.gridLine.copy(alpha = if (i == 0) 0.45f else 0.16f),
                                start = Offset(gx, dy),
                                end = Offset(gx, dy + tileDisplayPx),
                                strokeWidth = if (i == 0) 1.2f else 0.7f
                            )
                            drawLine(
                                color = accents.gridLine.copy(alpha = if (i == 0) 0.45f else 0.16f),
                                start = Offset(dx, gy),
                                end = Offset(dx + tileDisplayPx, gy),
                                strokeWidth = if (i == 0) 1.2f else 0.7f
                            )
                        }
                    }
                }
            }

            fun latLonToScreen(lat: Double, lon: Double): Offset {
                val pxX = screenCenterX + ((TileMath.lonToTileXDouble(lon, zoom) - centerTileX) * tileDisplayPx).toFloat()
                val pxY = screenCenterY + ((TileMath.latToTileYDouble(lat, zoom) - centerTileY) * tileDisplayPx).toFloat()
                return Offset(pxX, pxY)
            }

            // Draw recorded GNSS breadcrumb trail
            if (trackPoints.size >= 2) {
                val ordered = trackPoints.sortedBy { it.timestampMs }
                val path = Path()
                ordered.forEachIndexed { idx, pt ->
                    val p = latLonToScreen(pt.latitude, pt.longitude)
                    if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(
                    path = path,
                    color = accents.signalOrange,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Draw saved offline waypoints
            waypoints.forEach { wp ->
                val p = latLonToScreen(wp.latitude, wp.longitude)
                if (p.x in -40f..(size.width + 40f) && p.y in -40f..(size.height + 40f)) {
                    val pinColor = when (wp.category) {
                        "WATER" -> Color(0xFF38BDF8)
                        "HAZARD" -> accents.alertRed
                        "SUMMIT" -> accents.cautionAmber
                        else -> accents.contourMint
                    }
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.55f),
                        radius = 9.dp.toPx(),
                        center = p
                    )
                    drawCircle(
                        color = pinColor,
                        radius = 6.5.dp.toPx(),
                        center = p
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = p
                    )
                }
            }

            // Draw live GPS position puck + accuracy ring
            val gpsLat = telemetry.latitude
            val gpsLon = telemetry.longitude
            if (gpsLat != null && gpsLon != null) {
                val gpsPt = latLonToScreen(gpsLat, gpsLon)
                val accMeters = telemetry.accuracyMeters ?: 15f
                val metersPerPx = TileMath.metersPerPixel(gpsLat, zoom)
                val accPx = (accMeters / metersPerPx).toFloat().coerceIn(14.dp.toPx(), 120.dp.toPx())

                drawCircle(
                    color = accents.contourMint.copy(alpha = 0.18f),
                    radius = accPx,
                    center = gpsPt
                )
                drawCircle(
                    color = accents.contourMint.copy(alpha = 0.6f),
                    radius = accPx,
                    center = gpsPt,
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = gpsPt
                )
                drawCircle(
                    color = accents.contourMint,
                    radius = 6.dp.toPx(),
                    center = gpsPt
                )
            }

            // Center crosshair reticle when not locked to GPS
            if (!viewport.followGps) {
                val cross = 12.dp.toPx()
                drawLine(
                    color = accents.signalOrange.copy(alpha = 0.8f),
                    start = Offset(screenCenterX - cross, screenCenterY),
                    end = Offset(screenCenterX + cross, screenCenterY),
                    strokeWidth = 1.6.dp.toPx()
                )
                drawLine(
                    color = accents.signalOrange.copy(alpha = 0.8f),
                    start = Offset(screenCenterX, screenCenterY - cross),
                    end = Offset(screenCenterX, screenCenterY + cross),
                    strokeWidth = 1.6.dp.toPx()
                )
            }
        }

        // Top HUD Overlay: Fix & Speed + Coordinates
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinkAndFixStatusBar(
                fixState = telemetry.fixState,
                isNetworkOnline = telemetry.isNetworkOnline,
                offlineOnlyMode = preferences.offlineOnlyMode,
                satellitesUsed = telemetry.satellitesUsed,
                satellitesVisible = telemetry.satellitesVisible
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${CoordinateFormatter.formatSpeed(telemetry.speedMps, preferences.speedUnit)} ${preferences.speedUnit.label}",
                            style = TelemetryMonoLarge,
                            color = accents.signalOrange
                        )
                        val coordLat = telemetry.latitude ?: viewport.centerLat
                        val coordLon = telemetry.longitude ?: viewport.centerLon
                        Text(
                            text = CoordinateFormatter.formatSingleLine(
                                coordLat,
                                coordLon,
                                preferences.coordinateFormat
                            ),
                            style = TelemetryMonoSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Z$zoom • ${preferences.mapLayer.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.contourMint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (downloadProgress != null && downloadProgress.isActive) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = accents.contourMint.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accents.contourMint)
                ) {
                    Text(
                        text = "Caching \"${downloadProgress.regionName}\": ${downloadProgress.downloaded}/${downloadProgress.total} tiles (${CoordinateFormatter.formatBytes(downloadProgress.bytesSaved)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Right-side Map Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            SmallFloatingActionButton(
                onClick = {
                    val next = if (preferences.mapLayer == MapLayer.TOPO) MapLayer.STREET else MapLayer.TOPO
                    onToggleLayer(next)
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("map_layer_toggle_button")
            ) {
                Icon(Icons.Filled.Layers, contentDescription = "Switch map layer")
            }
            SmallFloatingActionButton(
                onClick = { onSetZoom(zoom + 1) },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("map_zoom_in_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom in")
            }
            SmallFloatingActionButton(
                onClick = { onSetZoom(zoom - 1) },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("map_zoom_out_button")
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
            }
            SmallFloatingActionButton(
                onClick = { showDropWaypointDialog = true },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = accents.signalOrange,
                modifier = Modifier.testTag("map_drop_waypoint_button")
            ) {
                Icon(Icons.Filled.AddLocationAlt, contentDescription = "Add waypoint at center")
            }
            FloatingActionButton(
                onClick = onCenterOnGps,
                shape = CircleShape,
                containerColor = if (viewport.followGps) accents.contourMint else MaterialTheme.colorScheme.surface,
                contentColor = if (viewport.followGps) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("map_center_gps_button")
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Center on GPS")
            }
        }

        // Bottom-left Quick Offline Area Save Button
        Surface(
            onClick = { showSaveAreaDialog = true },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, accents.contourMint.copy(alpha = 0.6f)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 18.dp)
                .testTag("map_cache_area_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.DownloadForOffline,
                    contentDescription = "Cache visible area offline",
                    tint = accents.contourMint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Cache Area",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showSaveAreaDialog) {
        QuickSaveAreaDialog(
            centerLat = viewport.centerLat,
            centerLon = viewport.centerLon,
            onDismiss = { showSaveAreaDialog = false },
            onDownload = { name, radiusKm, minZ, maxZ ->
                onDownloadViewportOffline(name, viewport.centerLat, viewport.centerLon, radiusKm, minZ, maxZ)
                showSaveAreaDialog = false
                Toast.makeText(context, "Offline caching started", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showDropWaypointDialog) {
        val targetLat = telemetry.latitude ?: viewport.centerLat
        val targetLon = telemetry.longitude ?: viewport.centerLon
        AddWaypointDialog(
            latitude = targetLat,
            longitude = targetLon,
            altitudeMeters = telemetry.altitudeMeters,
            onDismiss = { showDropWaypointDialog = false },
            onSave = { name, category, notes ->
                onAddWaypoint(name, category, targetLat, targetLon, telemetry.altitudeMeters, notes)
                showDropWaypointDialog = false
                Toast.makeText(context, "Waypoint saved offline", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun QuickSaveAreaDialog(
    centerLat: Double,
    centerLon: Double,
    onDismiss: () -> Unit,
    onDownload: (name: String, radiusKm: Double, minZoom: Int, maxZoom: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var radiusKm by remember { mutableStateOf(3.0) }
    var maxZoom by remember { mutableStateOf(14) }
    val bounds = remember(centerLat, centerLon, radiusKm) {
        TileMath.boundsAroundPoint(centerLat, centerLon, radiusKm)
    }
    val tileCount = remember(bounds, maxZoom) {
        TileMath.estimateTileCount(bounds, 10, maxZoom)
    }
    val estMb = String.format(java.util.Locale.US, "%.1f MB", (tileCount * 22.0) / 1024.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cache Map Area Offline") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Center: ${CoordinateFormatter.formatSingleLine(centerLat, centerLon, com.example.data.local.CoordinateFormat.DD)}",
                    style = TelemetryMonoSmall,
                    color = LocalTerraGridAccents.current.contourMint
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Region Name") },
                    placeholder = { Text("e.g. North Ridge Trail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Coverage Radius", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2.0 to "2 km", 5.0 to "5 km", 10.0 to "10 km").forEach { (r, label) ->
                        FilterChip(
                            selected = radiusKm == r,
                            onClick = { radiusKm = r },
                            label = { Text(label) }
                        )
                    }
                }
                Text("Detail Depth (Z10–Z$maxZoom)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(13 to "Z13 Fast", 14 to "Z14 Trail", 15 to "Z15 Max").forEach { (z, label) ->
                        FilterChip(
                            selected = maxZoom == z,
                            onClick = { maxZoom = z },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    text = "Estimated: $tileCount tiles (~$estMb)",
                    style = TelemetryMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onDownload(name, radiusKm, 10, maxZoom) },
                modifier = Modifier.testTag("confirm_cache_area_button")
            ) {
                Text("Download")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
