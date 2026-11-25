package com.example.data.maps

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.local.CachedTileEntity
import com.example.data.local.MapLayer
import com.example.data.local.OfflineRegionEntity
import com.example.data.local.TerraGridDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class RegionDownloadProgress(
    val regionName: String,
    val downloaded: Int,
    val total: Int,
    val bytesSaved: Long,
    val isActive: Boolean
)

class TileCacheRepository(
    private val context: Context,
    private val dao: TerraGridDao
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val memoryCache = LruCache<String, ImageBitmap>(110)
    private val tilesRootDir: File by lazy {
        File(context.filesDir, "map_tiles").apply { mkdirs() }
    }

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeDownloadJob: Job? = null

    private val _downloadProgress = MutableStateFlow<RegionDownloadProgress?>(null)
    val downloadProgress: StateFlow<RegionDownloadProgress?> = _downloadProgress.asStateFlow()

    fun getCachedMemoryTile(layer: MapLayer, z: Int, x: Int, y: Int): ImageBitmap? {
        return memoryCache.get("${layer.id}/$z/$x/$y")
    }

    suspend fun getOrFetchTile(
        layer: MapLayer,
        z: Int,
        x: Int,
        y: Int,
        allowNetwork: Boolean
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "${layer.id}/$z/$x/$y"
        memoryCache.get(key)?.let { return@withContext it }

        val tileFile = tileFileFor(layer.id, z, x, y)
        if (tileFile.exists() && tileFile.length() > 0L) {
            val bmp = decodeBitmap(tileFile)
            if (bmp != null) {
                memoryCache.put(key, bmp)
                return@withContext bmp
            }
        }

        if (!allowNetwork) return@withContext null

        val bytes = fetchTileBytes(layer, z, x, y) ?: return@withContext null
        runCatching {
            tileFile.parentFile?.mkdirs()
            tileFile.writeBytes(bytes)
            dao.insertTile(
                CachedTileEntity(
                    layerId = layer.id,
                    z = z,
                    x = x,
                    y = y,
                    regionId = null,
                    filePath = tileFile.absolutePath,
                    sizeBytes = bytes.size.toLong()
                )
            )
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            if (bitmap != null) {
                memoryCache.put(key, bitmap)
            }
            bitmap
        }.getOrNull()
    }

    fun startRegionDownload(
        name: String,
        centerLat: Double,
        centerLon: Double,
        bounds: LatLonBounds,
        minZoom: Int,
        maxZoom: Int,
        layer: MapLayer
    ) {
        activeDownloadJob?.cancel()
        activeDownloadJob = downloadScope.launch {
            val tiles = TileMath.enumerateTiles(bounds, minZoom, maxZoom, maxLimit = 1800)
            if (tiles.isEmpty()) return@launch

            val regionId = dao.insertRegion(
                OfflineRegionEntity(
                    name = name,
                    centerLat = centerLat,
                    centerLon = centerLon,
                    minLat = bounds.minLat,
                    maxLat = bounds.maxLat,
                    minLon = bounds.minLon,
                    maxLon = bounds.maxLon,
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    layerId = layer.id,
                    totalTiles = tiles.size,
                    downloadedTiles = 0,
                    totalBytes = 0L,
                    status = "DOWNLOADING"
                )
            )

            val doneCount = AtomicInteger(0)
            val totalBytes = AtomicLong(0L)
            val successCount = AtomicInteger(0)
            val semaphore = Semaphore(4)

            _downloadProgress.value = RegionDownloadProgress(
                regionName = name,
                downloaded = 0,
                total = tiles.size,
                bytesSaved = 0L,
                isActive = true
            )

            try {
                coroutineScope {
                    tiles.map { coord ->
                        async {
                            semaphore.withPermit {
                                val file = tileFileFor(layer.id, coord.z, coord.x, coord.y)
                                val bytesLen = if (file.exists() && file.length() > 0L) {
                                    val len = file.length()
                                    dao.insertTile(
                                        CachedTileEntity(
                                            layerId = layer.id,
                                            z = coord.z,
                                            x = coord.x,
                                            y = coord.y,
                                            regionId = regionId,
                                            filePath = file.absolutePath,
                                            sizeBytes = len
                                        )
                                    )
                                    successCount.incrementAndGet()
                                    len
                                } else {
                                    val fetched = fetchTileBytes(layer, coord.z, coord.x, coord.y)
                                    if (fetched != null) {
                                        file.parentFile?.mkdirs()
                                        file.writeBytes(fetched)
                                        val len = fetched.size.toLong()
                                        dao.insertTile(
                                            CachedTileEntity(
                                                layerId = layer.id,
                                                z = coord.z,
                                                x = coord.x,
                                                y = coord.y,
                                                regionId = regionId,
                                                filePath = file.absolutePath,
                                                sizeBytes = len
                                            )
                                        )
                                        BitmapFactory.decodeByteArray(fetched, 0, fetched.size)
                                            ?.asImageBitmap()
                                            ?.let { bmp ->
                                                memoryCache.put("${layer.id}/${coord.z}/${coord.x}/${coord.y}", bmp)
                                            }
                                        successCount.incrementAndGet()
                                        len
                                    } else {
                                        0L
                                    }
                                }

                                val currentBytes = totalBytes.addAndGet(bytesLen)
                                val currentDone = doneCount.incrementAndGet()
                                if (currentDone % 4 == 0 || currentDone == tiles.size) {
                                    _downloadProgress.value = RegionDownloadProgress(
                                        regionName = name,
                                        downloaded = currentDone,
                                        total = tiles.size,
                                        bytesSaved = currentBytes,
                                        isActive = true
                                    )
                                    dao.updateRegionProgress(
                                        regionId = regionId,
                                        downloadedTiles = successCount.get(),
                                        totalBytes = currentBytes,
                                        status = "DOWNLOADING"
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }

                val finalSuccess = successCount.get()
                val finalStatus = when {
                    finalSuccess == tiles.size -> "COMPLETE"
                    finalSuccess > 0 -> "PARTIAL"
                    else -> "FAILED"
                }
                dao.updateRegionProgress(regionId, finalSuccess, totalBytes.get(), finalStatus)
            } finally {
                _downloadProgress.value = null
            }
        }
    }

    fun cancelRegionDownload() {
        activeDownloadJob?.cancel()
        _downloadProgress.value = null
    }

    suspend fun deleteRegion(regionId: Long) = withContext(Dispatchers.IO) {
        val regionTiles = dao.getTilesForRegion(regionId)
        regionTiles.forEach { tile ->
            runCatching { File(tile.filePath).delete() }
            memoryCache.remove("${tile.layerId}/${tile.z}/${tile.x}/${tile.y}")
        }
        dao.deleteTilesForRegion(regionId)
        dao.deleteRegionById(regionId)
    }

    suspend fun clearAllTileCache() = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        tilesRootDir.deleteRecursively()
        tilesRootDir.mkdirs()
        dao.deleteAllTiles()
    }

    private fun tileFileFor(layerId: String, z: Int, x: Int, y: Int): File {
        return File(tilesRootDir, "$layerId/$z/$x/$y.png")
    }

    private fun decodeBitmap(file: File): ImageBitmap? {
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }.getOrNull()
    }

    private fun fetchTileBytes(layer: MapLayer, z: Int, x: Int, y: Int): ByteArray? {
        val url = when (layer) {
            MapLayer.TOPO -> "https://tile.opentopomap.org/$z/$x/$y.png"
            MapLayer.STREET -> "https://tile.openstreetmap.org/$z/$x/$y.png"
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "TerraGrid-Android/1.0 (offgrid-gps-explorer)")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.bytes()
                    if (body != null && body.size > 100) body else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}
