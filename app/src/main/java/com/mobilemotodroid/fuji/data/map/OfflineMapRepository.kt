package com.mobilemotodroid.fuji.data.map

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mobilemotodroid.fuji.data.settings.MapType
import com.mobilemotodroid.fuji.domain.GeoBounds
import com.mobilemotodroid.fuji.domain.GeoPoint
import com.mobilemotodroid.fuji.domain.GeoUtils
import com.mobilemotodroid.fuji.domain.TileMath
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineGeometryRegionDefinition
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionDefinition
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** ダウンロード済み（またはダウンロード中）の地図の情報（FR-OFF-07）。 */
data class OfflineRegionInfo(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val mapType: MapType,
    val bounds: GeoBounds?,
    val minTileZoom: Int,
    val maxTileZoom: Int,
    val completedBytes: Long,
    val completedCount: Long,
    val requiredCount: Long,
    val isComplete: Boolean,
    val isDownloading: Boolean,
) {
    val progress: Float get() = if (requiredCount <= 0) 0f else (completedCount.toFloat() / requiredCount).coerceIn(0f, 1f)
}

/** ダウンロード範囲の指定。 */
sealed interface DownloadArea {
    /** 地図上で指定した矩形（FR-OFF-01）。 */
    data class Rectangle(val bounds: GeoBounds) : DownloadArea

    /** ルートの周辺 [widthM] の範囲（FR-OFF-04）。 */
    data class RouteCorridor(val route: List<GeoPoint>, val widthM: Double) : DownloadArea {
        /** ルートを一定間隔で区切り、各点のまわりの正方形の集まりとして範囲を表す。 */
        fun boxes(): List<GeoBounds> =
            GeoUtils.resample(route, (widthM).coerceAtLeast(50.0)).map { GeoBounds.around(it, widthM) }
    }
}

/** ダウンロード前の見積もり（FR-OFF-02）。 */
data class DownloadEstimate(val tileCount: Long, val bytes: Long, val exceedsLimit: Boolean)

/**
 * MapLibre のオフライン機能を使った地図のダウンロード・管理（F2）。
 *
 * ズームは利用者に分かりやすい「タイルのズーム」（地理院タイルの z）で扱う。
 * MapLibre の地図ズームでは 256px のラスタータイルを 1 段上の z で読むため、
 * 領域の定義には「タイルのズーム − 1」を渡す。
 */
@Singleton
class OfflineMapRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
) {
    private val offlineManager: OfflineManager get() = OfflineManager.getInstance(context)

    fun estimate(area: DownloadArea, minTileZoom: Int, maxTileZoom: Int, mapType: MapType): DownloadEstimate {
        val count = when (area) {
            is DownloadArea.Rectangle -> TileMath.countTiles(area.bounds, minTileZoom, maxTileZoom)
            is DownloadArea.RouteCorridor ->
                TileMath.countTilesCovering(area.boxes(), minTileZoom, maxTileZoom, limit = MAX_TILES)
        }
        return DownloadEstimate(
            tileCount = count,
            bytes = count * MapStyles.averageTileBytes(mapType),
            exceedsLimit = count > MAX_TILES,
        )
    }

    /** 領域を作成し、ダウンロードを開始する。作成した領域の ID を返す。 */
    suspend fun createAndStart(
        name: String,
        area: DownloadArea,
        minTileZoom: Int,
        maxTileZoom: Int,
        mapType: MapType,
    ): Long {
        val estimate = estimate(area, minTileZoom, maxTileZoom, mapType)
        require(!estimate.exceedsLimit) { "範囲が広すぎます。範囲かズームを小さくしてください。" }

        val styleUrl = MapStyles.styleUri(mapType)
        val minZoom = (minTileZoom - 1).coerceAtLeast(0).toDouble()
        val maxZoom = (maxTileZoom - 1).coerceAtLeast(0).toDouble()
        val pixelRatio = context.resources.displayMetrics.density
        val regionAndBounds: Pair<OfflineRegionDefinition, GeoBounds?> = when (area) {
            is DownloadArea.Rectangle -> {
                val b = area.bounds
                OfflineTilePyramidRegionDefinition(
                    styleUrl,
                    LatLngBounds.from(b.north, b.east, b.south, b.west),
                    minZoom, maxZoom, pixelRatio,
                ) to b
            }
            is DownloadArea.RouteCorridor -> {
                val boxes = area.boxes()
                val polygons = boxes.map { b ->
                    listOf(
                        listOf(
                            Point.fromLngLat(b.west, b.south),
                            Point.fromLngLat(b.east, b.south),
                            Point.fromLngLat(b.east, b.north),
                            Point.fromLngLat(b.west, b.north),
                            Point.fromLngLat(b.west, b.south),
                        ),
                    )
                }
                OfflineGeometryRegionDefinition(
                    styleUrl, MultiPolygon.fromLngLats(polygons), minZoom, maxZoom, pixelRatio,
                ) to GeoBounds.of(area.route)?.expandedByMeters(area.widthM)
            }
        }
        val (definition, bounds) = regionAndBounds

        val metadata = JSONObject().apply {
            put(META_NAME, name)
            put(META_CREATED_AT, System.currentTimeMillis())
            put(META_MAP_TYPE, mapType.name)
            put(META_MIN_Z, minTileZoom)
            put(META_MAX_Z, maxTileZoom)
            bounds?.let {
                put(META_SOUTH, it.south); put(META_WEST, it.west)
                put(META_NORTH, it.north); put(META_EAST, it.east)
            }
        }.toString().toByteArray(Charsets.UTF_8)

        val region = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<OfflineRegion> { cont ->
                offlineManager.createOfflineRegion(
                    definition,
                    metadata,
                    object : OfflineManager.CreateOfflineRegionCallback {
                        override fun onCreate(offlineRegion: OfflineRegion) {
                            if (cont.isActive) cont.resume(offlineRegion)
                        }

                        override fun onError(error: String) {
                            if (cont.isActive) cont.resumeWithException(IllegalStateException(error))
                        }
                    },
                )
            }
        }
        start(region.id)
        return region.id
    }

    /** ダウンロードを開始・再開する（FR-OFF-05, 06）。アプリを閉じても WorkManager が続ける。 */
    fun start(regionId: Long) {
        val request = OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
            .setInputData(workDataOf(OfflineDownloadWorker.KEY_REGION_ID to regionId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(workName(regionId), ExistingWorkPolicy.KEEP, request)
    }

    /** ダウンロードを中断する（FR-OFF-05）。 */
    suspend fun pause(regionId: Long) {
        workManager.cancelUniqueWork(workName(regionId))
        withContext(Dispatchers.Main) {
            findRegion(regionId)?.let {
                it.setDownloadState(OfflineRegion.STATE_INACTIVE)
                it.setObserver(null)
            }
        }
    }

    /** FR-OFF-08 */
    suspend fun delete(regionId: Long) {
        workManager.cancelUniqueWork(workName(regionId))
        withContext(Dispatchers.Main) {
            val region = findRegion(regionId) ?: return@withContext
            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            region.setObserver(null)
            suspendCancellableCoroutine<Unit> { cont ->
                region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(error: String) {
                        if (cont.isActive) cont.resumeWithException(IllegalStateException(error))
                    }
                })
            }
        }
    }

    /** FR-OFF-07：一覧と各領域の進捗・容量。 */
    suspend fun list(): List<OfflineRegionInfo> = withContext(Dispatchers.Main) {
        listRegions().map { region ->
            val status = statusOf(region)
            toInfo(region, status)
        }.sortedByDescending { it.createdAt }
    }

    /**
     * 領域のダウンロードを実行し、完了するまで待つ（ワーカーから呼ぶ）。
     * 完了したら true、上限超過などで続けられないときは false を返す。
     * コルーチンがキャンセルされたら（中断操作）、ダウンロードを止める。
     */
    suspend fun downloadAndAwait(regionId: Long, onProgress: (OfflineRegionInfo) -> Unit): Boolean =
        withContext(Dispatchers.Main) {
            val region = findRegion(regionId) ?: return@withContext false
            suspendCancellableCoroutine<Boolean> { cont ->
                region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        onProgress(toInfo(region, status))
                        if (status.isComplete) {
                            region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                            region.setObserver(null)
                            if (cont.isActive) cont.resume(true)
                        }
                    }

                    override fun onError(error: OfflineRegionError) {
                        // 通信エラーなどは MapLibre が自動で再試行するので、記録だけ残す
                        Log.w(TAG, "offline download error: ${error.reason} ${error.message}")
                    }

                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        region.setObserver(null)
                        if (cont.isActive) cont.resume(false)
                    }
                })
                region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                cont.invokeOnCancellation {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        region.setObserver(null)
                    }
                }
            }
        }

    private suspend fun findRegion(regionId: Long): OfflineRegion? = listRegions().firstOrNull { it.id == regionId }

    private suspend fun listRegions(): List<OfflineRegion> = suspendCancellableCoroutine { cont ->
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                if (cont.isActive) cont.resume(offlineRegions?.toList().orEmpty())
            }

            override fun onError(error: String) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error))
            }
        })
    }

    private suspend fun statusOf(region: OfflineRegion): OfflineRegionStatus? = suspendCancellableCoroutine { cont ->
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (cont.isActive) cont.resume(status)
            }

            override fun onError(error: String?) {
                if (cont.isActive) cont.resume(null)
            }
        })
    }

    private fun toInfo(region: OfflineRegion, status: OfflineRegionStatus?): OfflineRegionInfo {
        val meta = runCatching { JSONObject(String(region.metadata, Charsets.UTF_8)) }.getOrNull() ?: JSONObject()
        val bounds = if (meta.has(META_SOUTH)) {
            runCatching {
                GeoBounds(
                    meta.getDouble(META_SOUTH), meta.getDouble(META_WEST),
                    meta.getDouble(META_NORTH), meta.getDouble(META_EAST),
                )
            }.getOrNull()
        } else {
            null
        }
        return OfflineRegionInfo(
            id = region.id,
            name = meta.optString(META_NAME, "地図 ${region.id}"),
            createdAt = meta.optLong(META_CREATED_AT, 0L),
            mapType = runCatching { MapType.valueOf(meta.optString(META_MAP_TYPE)) }.getOrDefault(MapType.STANDARD),
            bounds = bounds,
            minTileZoom = meta.optInt(META_MIN_Z, 0),
            maxTileZoom = meta.optInt(META_MAX_Z, 0),
            completedBytes = status?.completedResourceSize ?: 0L,
            completedCount = status?.completedResourceCount ?: 0L,
            requiredCount = status?.requiredResourceCount ?: 0L,
            // 一度もダウンロードしていない領域は 0/0 で「完了」になるため、必要数が 0 のときは未完了とみなす
            isComplete = status != null && status.requiredResourceCount > 0 && status.isComplete,
            isDownloading = status?.downloadState == OfflineRegion.STATE_ACTIVE,
        )
    }

    companion object {
        private const val TAG = "OfflineMap"
        const val WORK_TAG = "offline_download"

        /** 提供元への負荷を抑えるための、1 領域あたりのタイル数の上限（リスク対策）。 */
        const val MAX_TILES = 60_000L

        private const val META_NAME = "name"
        private const val META_CREATED_AT = "createdAt"
        private const val META_MAP_TYPE = "mapType"
        private const val META_MIN_Z = "minTileZoom"
        private const val META_MAX_Z = "maxTileZoom"
        private const val META_SOUTH = "south"
        private const val META_WEST = "west"
        private const val META_NORTH = "north"
        private const val META_EAST = "east"

        fun workName(regionId: Long) = "offline_download_$regionId"
    }
}
