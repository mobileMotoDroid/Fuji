package com.example.traillog.data.repository

import android.content.Context
import com.example.traillog.data.db.ActivityDao
import com.example.traillog.data.db.ActivityEntity
import com.example.traillog.data.db.ActivityStatus
import com.example.traillog.data.db.PhotoDao
import com.example.traillog.data.db.PhotoEntity
import com.example.traillog.data.db.RouteDao
import com.example.traillog.data.db.RouteEntity
import com.example.traillog.data.db.RoutePointEntity
import com.example.traillog.data.db.TrackPointDao
import com.example.traillog.data.db.TrackPointEntity
import com.example.traillog.data.gpx.GpxPoint
import com.example.traillog.data.gpx.GpxWriter
import com.example.traillog.domain.GeoPoint
import com.example.traillog.domain.GeoUtils
import com.example.traillog.domain.TrackAccumulator
import com.example.traillog.domain.TrackSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 活動記録（軌跡・写真を含む）の保存と取得。 */
@Singleton
class ActivityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityDao: ActivityDao,
    private val trackPointDao: TrackPointDao,
    private val photoDao: PhotoDao,
    private val routeDao: RouteDao,
) {
    fun observeFinished(): Flow<List<ActivityEntity>> = activityDao.observeFinished()
    fun observe(id: Long): Flow<ActivityEntity?> = activityDao.observe(id)
    fun observePhotos(activityId: Long): Flow<List<PhotoEntity>> = photoDao.observeForActivity(activityId)

    suspend fun get(id: Long): ActivityEntity? = activityDao.get(id)
    suspend fun findUnfinished(): ActivityEntity? = activityDao.findUnfinished()
    suspend fun update(activity: ActivityEntity) = activityDao.update(activity)

    suspend fun create(startedAt: Long, routeId: Long?): Long = activityDao.insert(
        ActivityEntity(
            title = defaultTitle(startedAt),
            startedAt = startedAt,
            status = ActivityStatus.RECORDING,
            resumedAt = startedAt,
            routeId = routeId,
        ),
    )

    /** FR-REC-04：位置を 1 点ずつ保存する。 */
    suspend fun appendPoint(point: TrackPointEntity): Long = trackPointDao.insert(point)

    suspend fun getPoints(activityId: Long): List<TrackPointEntity> = trackPointDao.getForActivity(activityId)
    suspend fun getLastPoint(activityId: Long): TrackPointEntity? = trackPointDao.getLast(activityId)

    /**
     * 記録を終了する。保存済みの点から統計を計算し直し、状態を FINISHED にする。
     * 正常終了とクラッシュ後の終了（FR-REC-05）の両方で使う。
     */
    suspend fun finish(activityId: Long, endedAt: Long): ActivityEntity? {
        val activity = activityDao.get(activityId) ?: return null
        if (activity.status == ActivityStatus.FINISHED) return activity
        val acc = TrackAccumulator()
        acc.addAll(getPoints(activityId).map { it.toSample() })
        val runningSec = activity.resumedAt
            ?.takeIf { activity.status == ActivityStatus.RECORDING }
            ?.let { ((endedAt - it) / 1000).coerceAtLeast(0L) } ?: 0L
        val finished = activity.copy(
            status = ActivityStatus.FINISHED,
            endedAt = endedAt,
            resumedAt = null,
            movingTimeSec = activity.movingTimeSec + runningSec,
            distanceM = acc.distanceM,
            ascentM = acc.ascentM,
            descentM = acc.descentM,
            maxAltitudeM = acc.maxAltitudeM,
            minAltitudeM = acc.minAltitudeM,
        )
        activityDao.update(finished)
        return finished
    }

    suspend fun updateTitleMemo(id: Long, title: String, memo: String) =
        activityDao.updateTitleMemo(id, title.ifBlank { "無題の山行" }, memo)

    /** FR-ACT-08：活動を削除する（軌跡・写真の行は CASCADE で消え、写真ファイルもここで消す）。 */
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        photoDao.getForActivity(id).forEach { File(it.filePath).delete() }
        activityDao.delete(id)
    }

    /** FR-ACT-05：写真を活動に追加する。 */
    suspend fun addPhoto(activityId: Long, file: File, takenAt: Long, location: GeoPoint?): Long =
        photoDao.insert(
            PhotoEntity(
                activityId = activityId,
                filePath = file.absolutePath,
                takenAt = takenAt,
                latitude = location?.latitude,
                longitude = location?.longitude,
            ),
        )

    /** 端末の写真（Photo Picker で選んだもの）をアプリ専用領域にコピーして追加する。 */
    suspend fun importPhoto(activityId: Long, uri: android.net.Uri, location: GeoPoint?): Long {
        val file = newPhotoFile()
        withContext(Dispatchers.IO) {
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "写真を開けませんでした。" }
            input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
        }
        return addPhoto(activityId, file, System.currentTimeMillis(), location)
    }

    /** 写真の保存先。アプリ専用領域なので他のアプリからは見えない。 */
    fun newPhotoFile(): File {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        return File(dir, "photo_${System.currentTimeMillis()}.jpg")
    }

    /** FR-ACT-09：GPX 1.1 で書き出す。共有用にキャッシュ領域へ保存する。 */
    suspend fun exportGpx(activityId: Long): File = withContext(Dispatchers.IO) {
        val activity = requireNotNull(activityDao.get(activityId)) { "activity not found" }
        val segments = getPoints(activityId)
            .groupBy { it.segment }
            .toSortedMap()
            .values
            .map { points -> points.map { GpxPoint(it.latitude, it.longitude, it.altitude, it.time) } }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = activity.title.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(60)
        val file = File(dir, "${safeName}_${activity.id}.gpx")
        file.bufferedWriter(Charsets.UTF_8).use { out ->
            GpxWriter.writeTrack(out, activity.title, activity.memo, segments)
        }
        file
    }

    /** FR-ACT-10：活動の軌跡を予定ルートとして保存する。 */
    suspend fun saveAsRoute(activityId: Long): Long {
        val activity = requireNotNull(activityDao.get(activityId)) { "activity not found" }
        val points = getPoints(activityId)
        require(points.size >= 2) { "軌跡の点が少ないため、ルートにできません。" }
        val geo = points.map { GeoPoint(it.latitude, it.longitude) }
        return routeDao.insertWithPoints(
            RouteEntity(name = activity.title, distanceM = GeoUtils.polylineLengthM(geo), importedAt = System.currentTimeMillis()),
            points.mapIndexed { i, p ->
                RoutePointEntity(routeId = 0, seq = i, latitude = p.latitude, longitude = p.longitude, altitude = p.altitude)
            },
        )
    }

    companion object {
        fun defaultTitle(startedAt: Long): String =
            SimpleDateFormat("yyyy/MM/dd HH:mm の山行", Locale.JAPAN).format(Date(startedAt))
    }
}

fun TrackPointEntity.toSample() = TrackSample(time, latitude, longitude, altitude, segment)
