package com.example.traillog.domain

/**
 * 気圧高度と GPS 高度を組み合わせて標高を求める（FR-STA-04）。
 *
 * 気圧高度は短時間の変化に強いが、天候で全体がずれる。GPS 高度は長期的には正しいが揺らぎが大きい。
 * そこで「GPS 高度 − 気圧高度」のずれ（オフセット）を少しずつ学習し、気圧高度に足して使う
 * （相補フィルタ）。気圧センサーがない端末では GPS 高度をそのまま返す。
 */
class AltitudeFuser(
    private val alpha: Double = 0.05,
    private val maxGpsVerticalAccuracyM: Float = 30f,
) {
    private var offset: Double? = null

    fun fuse(gpsAltitudeM: Double?, gpsVerticalAccuracyM: Float?, barometricAltitudeM: Double?): Double? {
        if (barometricAltitudeM == null) return gpsAltitudeM

        val gpsUsable = gpsAltitudeM != null &&
            (gpsVerticalAccuracyM == null || gpsVerticalAccuracyM <= maxGpsVerticalAccuracyM)
        if (gpsUsable) {
            val target = gpsAltitudeM!! - barometricAltitudeM
            offset = offset?.let { it + alpha * (target - it) } ?: target
        }
        return offset?.let { barometricAltitudeM + it } ?: gpsAltitudeM
    }

    fun reset() {
        offset = null
    }
}
