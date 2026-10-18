package com.mobilemotodroid.fuji.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mobilemotodroid.fuji.data.db.RouteDao
import com.mobilemotodroid.fuji.data.db.RouteEntity
import com.mobilemotodroid.fuji.data.db.RoutePointEntity
import com.mobilemotodroid.fuji.data.gpx.GpxParseException
import com.mobilemotodroid.fuji.data.gpx.GpxParser
import com.mobilemotodroid.fuji.domain.GeoPoint
import com.mobilemotodroid.fuji.domain.GeoUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class RouteWithPoints(val route: RouteEntity, val points: List<GeoPoint>)

/** 予定ルート（GPX から読み込んだもの）の管理。 */
@Singleton
class RouteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routeDao: RouteDao,
) {
    fun observeAll(): Flow<List<RouteEntity>> = routeDao.observeAll()

    suspend fun get(id: Long): RouteEntity? = routeDao.get(id)

    suspend fun getWithPoints(id: Long): RouteWithPoints? {
        val route = routeDao.get(id) ?: return null
        return RouteWithPoints(route, routeDao.getPoints(id).map { GeoPoint(it.latitude, it.longitude) })
    }

    suspend fun delete(id: Long) = routeDao.delete(id)

    /**
     * FR-RTE-01：GPX を読み込んで保存し、ルートの ID を返す。
     * 形式が不正なときは [GpxParseException] を投げる（FR-RTE-07）。
     */
    @Throws(GpxParseException::class)
    suspend fun importGpx(uri: Uri): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
        if (size != null && size > MAX_GPX_BYTES) {
            throw GpxParseException("ファイルが大きすぎます（上限 ${MAX_GPX_BYTES / 1024 / 1024} MB）。")
        }
        val parsed = try {
            resolver.openInputStream(uri)?.use { GpxParser.parse(it) }
        } catch (e: SecurityException) {
            throw GpxParseException("ファイルを開く権限がありません。", e)
        }
        val data = parsed ?: throw GpxParseException("ファイルを開けませんでした。")

        val geometry = data.routeGeometry()
        val geoPoints = geometry.map { GeoPoint(it.latitude, it.longitude) }
        val name = data.name ?: displayName(uri)?.removeSuffix(".gpx")?.removeSuffix(".GPX") ?: "ルート"
        routeDao.insertWithPoints(
            RouteEntity(name = name, distanceM = GeoUtils.polylineLengthM(geoPoints), importedAt = System.currentTimeMillis()),
            geometry.mapIndexed { i, p ->
                RoutePointEntity(routeId = 0, seq = i, latitude = p.latitude, longitude = p.longitude, altitude = p.elevationM)
            },
        )
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }

    companion object {
        private const val MAX_GPX_BYTES = 30L * 1024 * 1024
    }
}
