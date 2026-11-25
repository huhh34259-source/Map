package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.FieldIntelAndSettingsScreen
import com.example.ui.screens.OfflineCacheScreen
import com.example.ui.screens.OfflineMapScreen
import com.example.ui.screens.TelemetryScreen
import com.example.ui.theme.LocalTerraGridAccents
import com.example.ui.theme.TerraGridTheme

object TerraGridRoutes {
    const val TELEMETRY = "telemetry"
    const val MAP = "map"
    const val OFFLINE = "offline"
    const val INTEL = "intel"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    NavDestination(
        route = TerraGridRoutes.TELEMETRY,
        label = "HUD",
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
        testTag = "nav_hud"
    ),
    NavDestination(
        route = TerraGridRoutes.MAP,
        label = "Topo Map",
        selectedIcon = Icons.Filled.Map,
        unselectedIcon = Icons.Outlined.Map,
        testTag = "nav_map"
    ),
    NavDestination(
        route = TerraGridRoutes.OFFLINE,
        label = "Offline",
        selectedIcon = Icons.Filled.CloudDownload,
        unselectedIcon = Icons.Outlined.CloudDownload,
        testTag = "nav_offline"
    ),
    NavDestination(
        route = TerraGridRoutes.INTEL,
        label = "Intel",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_intel"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerraGridApp(viewModel: TerraGridViewModel = viewModel()) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val tripMetrics by viewModel.tripMetrics.collectAsStateWithLifecycle()
    val mapViewport by viewModel.mapViewport.collectAsStateWithLifecycle()
    val offlineRegions by viewModel.offlineRegions.collectAsStateWithLifecycle()
    val tileStats by viewModel.tileStats.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val trackPoints by viewModel.trackPoints.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val intelState by viewModel.intelState.collectAsStateWithLifecycle()
    val savedIntel by viewModel.savedIntel.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            viewModel.onLocationPermissionResult(true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    TerraGridTheme(darkTheme = preferences.darkHud) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: TerraGridRoutes.TELEMETRY
        val accents = LocalTerraGridAccents.current

        fun navigateTo(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    if (currentRoute != TerraGridRoutes.MAP) {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "TERRAGRID",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = accents.contourMint
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Destinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Destinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = TerraGridRoutes.TELEMETRY,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(TerraGridRoutes.TELEMETRY) {
                            TelemetryScreen(
                                telemetry = telemetry,
                                preferences = preferences,
                                tripMetrics = tripMetrics,
                                onRequestLocationPermission = {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                onSelectSpeedUnit = viewModel::setSpeedUnit,
                                onSelectCoordinateFormat = viewModel::setCoordinateFormat,
                                onToggleTripRecording = viewModel::toggleTripRecording,
                                onResetTrip = viewModel::resetTrip,
                                onAddWaypoint = viewModel::addWaypoint,
                                onOpenMap = { navigateTo(TerraGridRoutes.MAP) }
                            )
                        }

                        composable(TerraGridRoutes.MAP) {
                            OfflineMapScreen(
                                telemetry = telemetry,
                                preferences = preferences,
                                viewport = mapViewport,
                                waypoints = waypoints,
                                trackPoints = trackPoints,
                                downloadProgress = downloadProgress,
                                loadTile = viewModel::loadMapTile,
                                onPanMap = viewModel::panMapByPixels,
                                onSetZoom = viewModel::setMapZoom,
                                onCenterOnGps = viewModel::centerMapOnGps,
                                onToggleLayer = viewModel::setMapLayer,
                                onAddWaypoint = viewModel::addWaypoint,
                                onDownloadViewportOffline = viewModel::downloadOfflineRegion
                            )
                        }

                        composable(TerraGridRoutes.OFFLINE) {
                            OfflineCacheScreen(
                                telemetry = telemetry,
                                preferences = preferences,
                                viewport = mapViewport,
                                tileStats = tileStats,
                                regions = offlineRegions,
                                waypoints = waypoints,
                                presets = viewModel.remotePresets,
                                downloadProgress = downloadProgress,
                                onDownloadRegion = viewModel::downloadOfflineRegion,
                                onCancelDownload = viewModel::cancelDownload,
                                onDeleteRegion = viewModel::deleteRegion,
                                onClearAllCache = viewModel::clearAllTileCache,
                                onJumpToMap = { lat, lon, zoom ->
                                    viewModel.jumpToCoordinate(lat, lon, zoom)
                                    navigateTo(TerraGridRoutes.MAP)
                                },
                                onDeleteWaypoint = viewModel::deleteWaypoint
                            )
                        }

                        composable(TerraGridRoutes.INTEL) {
                            FieldIntelAndSettingsScreen(
                                telemetry = telemetry,
                                preferences = preferences,
                                intelState = intelState,
                                savedIntel = savedIntel,
                                onQueryIntel = viewModel::queryFieldIntel,
                                onDeleteSavedIntel = viewModel::deleteSavedIntel,
                                onSelectSpeedUnit = viewModel::setSpeedUnit,
                                onSelectCoordinateFormat = viewModel::setCoordinateFormat,
                                onSelectMapLayer = viewModel::setMapLayer,
                                onToggleOfflineOnly = viewModel::setOfflineOnlyMode,
                                onToggleDarkHud = viewModel::setDarkHud
                            )
                        }
                    }
                }
            }
        }
    }
}
