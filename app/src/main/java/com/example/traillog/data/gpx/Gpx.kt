package com.example.traillog.data.gpx

import com.example.traillog.domain.GeoPoint

/** GPX の 1 点。 */
data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationM: Double? = null,
    val timeMs: Long? = null,
)

/** 読み込んだ GPX の内容。 */
data class GpxData(
    val name: String?,
    /** trk 要素。trkseg ごとの点の列。 */
    val trackSegments: List<List<GpxPoint>>,
    /** rte 要素の点。 */
    val routePoints: List<GpxPoint>,
) {
    /**
     * 予定ルートとして使う点列（FR-RTE-01）。
     * trk があればその全区間をつなげたもの、なければ rte の点を使う。
     */
    fun routeGeometry(): List<GpxPoint> {
        val track = trackSegments.flatten()
        return track.ifEmpty { routePoints }
    }
}

fun GpxPoint.toGeoPoint() = GeoPoint(latitude, longitude)

/** GPX の形式が不正なときの例外（FR-RTE-07）。message はそのまま利用者に表示する。 */
class GpxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
