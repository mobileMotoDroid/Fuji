package com.mobilemotodroid.fuji.domain

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** 緯度経度の 1 点。 */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** 緯度経度の矩形範囲。 */
data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    init {
        require(south <= north) { "south must be <= north" }
        require(west <= east) { "west must be <= east" }
    }

    val center: GeoPoint get() = GeoPoint((south + north) / 2.0, (west + east) / 2.0)

    /** 四方に [meters] だけ広げた範囲を返す。 */
    fun expandedByMeters(meters: Double): GeoBounds {
        val dLat = meters / METERS_PER_DEGREE_LAT
        val cosLat = max(cos(Math.toRadians(center.latitude)), 0.01)
        val dLon = meters / (METERS_PER_DEGREE_LAT * cosLat)
        return GeoBounds(
            south = max(-85.0, south - dLat),
            west = max(-180.0, west - dLon),
            north = min(85.0, north + dLat),
            east = min(180.0, east + dLon),
        )
    }

    companion object {
        const val METERS_PER_DEGREE_LAT = 111_320.0

        fun of(points: List<GeoPoint>): GeoBounds? {
            if (points.isEmpty()) return null
            return GeoBounds(
                south = points.minOf { it.latitude },
                west = points.minOf { it.longitude },
                north = points.maxOf { it.latitude },
                east = points.maxOf { it.longitude },
            )
        }

        fun around(point: GeoPoint, radiusM: Double): GeoBounds =
            GeoBounds(point.latitude, point.longitude, point.latitude, point.longitude)
                .expandedByMeters(radiusM)
    }
}
