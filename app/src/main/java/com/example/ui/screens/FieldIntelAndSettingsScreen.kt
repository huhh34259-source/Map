package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CoordinateFormat
import com.example.data.local.FieldIntelEntity
import com.example.data.local.MapLayer
import com.example.data.local.SpeedUnit
import com.example.data.local.UserPreferences
import com.example.data.location.CoordinateFormatter
import com.example.data.location.TelemetrySnapshot
import com.example.ui.IntelUiState
import com.example.ui.theme.LocalTerraGridAccents
import com.example.ui.theme.TelemetryMonoSmall

@Composable
fun FieldIntelAndSettingsScreen(
    telemetry: TelemetrySnapshot,
    preferences: UserPreferences,
    intelState: IntelUiState,
    savedIntel: List<FieldIntelEntity>,
    onQueryIntel: (String) -> Unit,
    onDeleteSavedIntel: (Long) -> Unit,
    onSelectSpeedUnit: (SpeedUnit) -> Unit,
    onSelectCoordinateFormat: (CoordinateFormat) -> Unit,
    onSelectMapLayer: (MapLayer) -> Unit,
    onToggleOfflineOnly: (Boolean) -> Unit,
    onToggleDarkHud: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accents = LocalTerraGridAccents.current
    var query by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Maps-Grounded Field Intel Section
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.TravelExplore,
                            contentDescription = "Field Intelligence",
                            tint = accents.contourMint
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AREA FIELD BRIEFING",
                            style = MaterialTheme.typography.labelMedium,
                            color = accents.contourMint
                        )
                    }

                    // Mandatory in-app notice when Firebase AI Logic is disabled
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = accents.cautionAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accents.cautionAmber.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Notice",
                                tint = accents.cautionAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI & live Maps-grounded briefings require Firebase AI Logic. Currently displaying sample placeholder field responses that are cached locally for offline review.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search nearby trails, water, ranger posts...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("intel_query_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Water & Springs", "Camp & Shelter", "Emergency & Ranger").forEach { topic ->
                            FilterChip(
                                selected = query == topic,
                                onClick = {
                                    query = topic
                                    onQueryIntel(topic)
                                },
                                label = { Text(topic, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Button(
                        onClick = { onQueryIntel(query) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("query_intel_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Briefing at Coordinates")
                    }

                    when (intelState) {
                        is IntelUiState.Loading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Preparing briefing…")
                            }
                        }
                        is IntelUiState.Error -> {
                            Text(
                                text = intelState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = accents.alertRed
                            )
                        }
                        is IntelUiState.Ready -> {
                            val report = intelState.report
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (report.isPlaceholder) {
                                        Text(
                                            text = "SAMPLE PLACEHOLDER BRIEFING",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accents.cautionAmber,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = report.overview,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    report.places.forEach { place ->
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = place.name,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = place.estimatedRange,
                                                        style = TelemetryMonoSmall,
                                                        color = accents.contourMint
                                                    )
                                                }
                                                Text(
                                                    text = place.category,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = accents.signalOrange
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = place.summary,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        IntelUiState.Idle -> Unit
                    }
                }
            }
        }

        // Expedition Settings & Off-Grid Controls
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "EXPEDITION PREFERENCES",
                        style = MaterialTheme.typography.labelMedium,
                        color = accents.signalOrange
                    )

                    // Force Offline-Only Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CloudOff,
                                    contentDescription = null,
                                    tint = accents.cautionAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Force Off-Grid Cache Mode", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                text = "Serve maps strictly from local Room/disk cache and save battery in weak signal zones.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.offlineOnlyMode,
                            onCheckedChange = onToggleOfflineOnly,
                            modifier = Modifier.testTag("offline_only_switch")
                        )
                    }

                    // Dark Tactical HUD Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dark Tactical HUD", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Switch between low-glare night HUD and high-contrast daylight topo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.darkHud,
                            onCheckedChange = onToggleDarkHud,
                            modifier = Modifier.testTag("dark_hud_switch")
                        )
                    }

                    Text("Speed Unit", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpeedUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = preferences.speedUnit == unit,
                                onClick = { onSelectSpeedUnit(unit) },
                                label = { Text(unit.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Coordinate Format", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CoordinateFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = preferences.coordinateFormat == fmt,
                                onClick = { onSelectCoordinateFormat(fmt) },
                                label = { Text(fmt.shortLabel) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Default Map Source", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MapLayer.entries.forEach { layer ->
                            FilterChip(
                                selected = preferences.mapLayer == layer,
                                onClick = { onSelectMapLayer(layer) },
                                label = { Text(layer.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Cached Offline Briefings History
        if (savedIntel.isNotEmpty()) {
            item {
                Text(
                    text = "CACHED BRIEFINGS (${savedIntel.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                )
            }
            items(savedIntel, key = { it.id }) { item ->
                Surface(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.query,
                                style = MaterialTheme.typography.titleSmall,
                                color = accents.contourMint,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeleteSavedIntel(item.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete briefing", tint = accents.alertRed)
                            }
                        }
                        Text(
                            text = CoordinateFormatter.formatSingleLine(
                                item.latitude,
                                item.longitude,
                                preferences.coordinateFormat
                            ),
                            style = TelemetryMonoSmall,
                            color = accents.signalOrange
                        )
                        Text(
                            text = item.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (item.highlights.isNotBlank()) {
                            Text(
                                text = item.highlights,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
