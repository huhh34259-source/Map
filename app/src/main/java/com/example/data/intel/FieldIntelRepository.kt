package com.example.data.intel

import com.example.data.location.CoordinateFormatter
import com.example.data.local.CoordinateFormat
import java.util.Locale

data class FieldIntelPlace(
    val name: String,
    val category: String,
    val summary: String,
    val estimatedRange: String
)

data class FieldIntelReport(
    val query: String,
    val latitude: Double,
    val longitude: Double,
    val overview: String,
    val places: List<FieldIntelPlace>,
    val isPlaceholder: Boolean
)

/**
 * Abstraction for Gemini Maps-grounded field intelligence.
 * When Firebase AI Logic is enabled, a Firebase-backed implementation can replace
 * [PlaceholderFieldIntelRepository] without changing ViewModel or UI contracts.
 */
interface FieldIntelRepository {
    suspend fun queryAreaIntel(
        latitude: Double,
        longitude: Double,
        query: String
    ): Result<FieldIntelReport>
}

class PlaceholderFieldIntelRepository : FieldIntelRepository {
    override suspend fun queryAreaIntel(
        latitude: Double,
        longitude: Double,
        query: String
    ): Result<FieldIntelReport> {
        val coordText = CoordinateFormatter.formatSingleLine(latitude, longitude, CoordinateFormat.DD)
        val grid = CoordinateFormatter.toMaidenhead(latitude, longitude)
        val lower = query.lowercase(Locale.US)

        val places = when {
            lower.contains("water") || lower.contains("spring") -> listOf(
                FieldIntelPlace(
                    name = "Sample Ridge Creek Crossing",
                    category = "Water Source (Sample)",
                    summary = "Seasonal stream catchment along contour saddle; filter/boil recommended.",
                    estimatedRange = "~1.4 km NE"
                ),
                FieldIntelPlace(
                    name = "Sample Valley Spring Basin",
                    category = "Freshwater Spring (Sample)",
                    summary = "Protected rocky basin near drainage gully.",
                    estimatedRange = "~2.8 km SW"
                )
            )
            lower.contains("emergency") || lower.contains("ranger") -> listOf(
                FieldIntelPlace(
                    name = "Sample Sector Ranger Outpost",
                    category = "Ranger Station (Sample)",
                    summary = "VHF radio relay & emergency trailhead access road.",
                    estimatedRange = "~4.2 km SE"
                ),
                FieldIntelPlace(
                    name = "Sample Helipad / Clearing",
                    category = "Emergency LZ (Sample)",
                    summary = "Open flat ridge clearing suitable for emergency extraction.",
                    estimatedRange = "~2.1 km N"
                )
            )
            lower.contains("shelter") || lower.contains("camp") -> listOf(
                FieldIntelPlace(
                    name = "Sample Timberline Bivouac",
                    category = "Camp Shelter (Sample)",
                    summary = "Wind-sheltered flat terrace above creek bed.",
                    estimatedRange = "~0.9 km NW"
                ),
                FieldIntelPlace(
                    name = "Sample Timber Hut Post",
                    category = "Refuge Hut (Sample)",
                    summary = "Basic weatherproof emergency cabin on ridge junction.",
                    estimatedRange = "~3.1 km E"
                )
            )
            else -> listOf(
                FieldIntelPlace(
                    name = "Sample High-Ground Trailhead",
                    category = "Trail Access (Sample)",
                    summary = "Primary ridge spur junction connecting valley and summit routes.",
                    estimatedRange = "~1.8 km S"
                ),
                FieldIntelPlace(
                    name = "Sample Lookout Saddle",
                    category = "Terrain Landmark (Sample)",
                    summary = "Clear line-of-sight ridge useful for GNSS sky visibility.",
                    estimatedRange = "~2.5 km NE"
                ),
                FieldIntelPlace(
                    name = "Sample Creek Water Point",
                    category = "Water & Camp (Sample)",
                    summary = "Reliable drainage basin; verify offline topo map contours before descent.",
                    estimatedRange = "~1.6 km W"
                )
            )
        }

        val overview = "Sample offline placeholder briefing for $coordText (Grid $grid). " +
            "Query: \"$query\". Enable Firebase AI Logic to fetch live Gemini Maps-grounded " +
            "places, trail statuses, and real-time terrain updates."

        return Result.success(
            FieldIntelReport(
                query = query,
                latitude = latitude,
                longitude = longitude,
                overview = overview,
                places = places,
                isPlaceholder = true
            )
        )
    }
}
