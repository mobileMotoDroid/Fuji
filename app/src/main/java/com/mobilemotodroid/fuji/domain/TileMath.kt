package com.mobilemotodroid.fuji.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * XYZ タイル（地理院タイルと同じ方式）の計算。オフライン地図の推定タイル数・容量に使う（FR-OFF-02）。
 */
object TileMath {
    private const val MAX_LAT = 85.05112878

    fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    fun latToTileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val latRad = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT))
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        return floor(y).toInt().coerceIn(0, n - 1)
    }

    /** 矩形 [bounds] をズーム [minZoom]〜[maxZoom] で覆うタイル数。 */
    fun countTiles(bounds: GeoBounds, minZoom: Int, maxZoom: Int): Long {
        var total = 0L
        for (z in minZoom..maxZoom) {
            val x1 = lonToTileX(bounds.west, z)
            val x2 = lonToTileX(bounds.east, z)
            val y1 = latToTileY(bounds.north, z)
            val y2 = latToTileY(bounds.south, z)
            total += (x2 - x1 + 1).toLong() * (y2 - y1 + 1).toLong()
        }
        return total
    }

    /**
     * 複数の矩形（ルート周辺など）を覆うタイル数。重複するタイルは 1 つと数える。
     * [limit] を超えた時点で計算を打ち切り、[limit] + 1 を返す。
     */
    fun countTilesCovering(boxes: List<GeoBounds>, minZoom: Int, maxZoom: Int, limit: Long = Long.MAX_VALUE): Long {
        var total = 0L
        for (z in minZoom..maxZoom) {
            val keys = HashSet<Long>()
            for (box in boxes) {
                val x1 = lonToTileX(box.west, z)
                val x2 = lonToTileX(box.east, z)
                val y1 = latToTileY(box.north, z)
                val y2 = latToTileY(box.south, z)
                for (x in x1..x2) {
                    for (y in y1..y2) {
                        keys += (x.toLong() shl 32) or y.toLong()
                        if (total + keys.size > limit) return limit + 1
                    }
                }
            }
            total += keys.size
        }
        return total
    }

    /**
     * 地図のズームでの、1 dp あたりのメートル数（縮尺バー用、FR-MAP-06）。
     * MapLibre のズームは 512 dp を世界 1 周とする定義。
     */
    fun metersPerDp(latitude: Double, zoom: Double): Double {
        val worldDp = 512.0 * Math.pow(2.0, zoom)
        return cos(Math.toRadians(latitude)) * 2 * PI * WEB_MERCATOR_RADIUS_M / worldDp
    }

    /** Web メルカトル（地図タイル）で使う地球の半径。 */
    private const val WEB_MERCATOR_RADIUS_M = 6_378_137.0
}
