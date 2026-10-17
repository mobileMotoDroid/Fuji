package com.example.traillog.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** 距離計算などの地理計算。 */
object GeoUtils {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** 2 点間の距離（m）。ハバーサイン公式。 */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinLat = sin(dLat / 2)
        val sinLon = sin(dLon / 2)
        val a = sinLat * sinLat +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinLon * sinLon
        return 2 * EARTH_RADIUS_M * asin(sqrt(min(1.0, a)))
    }

    fun distanceM(a: GeoPoint, b: GeoPoint): Double =
        distanceM(a.latitude, a.longitude, b.latitude, b.longitude)

    /** 折れ線の長さ（m）。 */
    fun polylineLengthM(points: List<GeoPoint>): Double {
        var total = 0.0
        for (i in 1 until points.size) total += distanceM(points[i - 1], points[i])
        return total
    }

    /**
     * 点から折れ線までの最短距離（m）。
     * 点の周囲を平面とみなす近似で計算する（ルート逸脱判定の用途では十分な精度）。
     */
    fun distanceToPolylineM(point: GeoPoint, polyline: List<GeoPoint>): Double {
        if (polyline.isEmpty()) return Double.POSITIVE_INFINITY
        if (polyline.size == 1) return distanceM(point, polyline[0])

        val metersPerDegLat = PI * EARTH_RADIUS_M / 180.0
        val metersPerDegLon = metersPerDegLat * cos(Math.toRadians(point.latitude))
        var best = Double.POSITIVE_INFINITY
        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val ax = (a.longitude - point.longitude) * metersPerDegLon
            val ay = (a.latitude - point.latitude) * metersPerDegLat
            val bx = (b.longitude - point.longitude) * metersPerDegLon
            val by = (b.latitude - point.latitude) * metersPerDegLat
            val dx = bx - ax
            val dy = by - ay
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSq).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val d = sqrt(px * px + py * py)
            if (d < best) best = d
        }
        return best
    }

    /**
     * 折れ線を、およそ [intervalM] ごとの点に間引く（ルート周辺のダウンロード範囲計算などに使う）。
     * 始点と終点は必ず含める。
     */
    fun resample(polyline: List<GeoPoint>, intervalM: Double): List<GeoPoint> {
        if (polyline.size < 2) return polyline
        val result = mutableListOf(polyline.first())
        var carried = 0.0
        for (i in 1 until polyline.size) {
            val prev = polyline[i - 1]
            val cur = polyline[i]
            val segment = distanceM(prev, cur)
            var position = intervalM - carried
            while (position <= segment) {
                val t = position / segment
                result += GeoPoint(
                    prev.latitude + (cur.latitude - prev.latitude) * t,
                    prev.longitude + (cur.longitude - prev.longitude) * t,
                )
                position += intervalM
            }
            carried = (carried + segment) % intervalM
        }
        if (result.last() != polyline.last()) result += polyline.last()
        return result
    }
}
