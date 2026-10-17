package com.example.traillog.domain

/** フィルタにかける位置情報。 */
data class LocationFix(
    val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
)

/**
 * 記録する点を選別するフィルタ（FR-REC-07, 08, 09）。
 *
 * - 精度（accuracy）が [maxAccuracyM] より悪い点を除外する
 * - 直前に採用した点からの速度が [maxSpeedKmh] を超える点を除外する
 * - [stationaryRadiusM] 以内に [stationaryThinAfterMs] 以上とどまっている間は、
 *   [stationaryKeepIntervalMs] ごとに 1 点だけ残す
 */
class LocationFilter(
    private val maxAccuracyM: Float = 50f,
    private val maxSpeedKmh: Double = 30.0,
    private val stationaryRadiusM: Double = 10.0,
    private val stationaryThinAfterMs: Long = 5 * 60_000L,
    private val stationaryKeepIntervalMs: Long = 5 * 60_000L,
) {
    enum class Decision { ACCEPT, REJECT_ACCURACY, REJECT_SPEED, REJECT_DUPLICATE, THINNED }

    private var lastAccepted: LocationFix? = null

    /** 静止判定の起点（ここから [stationaryRadiusM] 以内にいる間は「止まっている」とみなす）。 */
    private var anchor: LocationFix? = null

    fun evaluate(fix: LocationFix): Decision {
        if (fix.accuracyM > maxAccuracyM) return Decision.REJECT_ACCURACY

        val last = lastAccepted
        val currentAnchor = anchor
        if (last == null || currentAnchor == null) {
            lastAccepted = fix
            anchor = fix
            return Decision.ACCEPT
        }

        val dtMs = fix.timeMs - last.timeMs
        if (dtMs <= 0) return Decision.REJECT_DUPLICATE

        val distance = GeoUtils.distanceM(last.latitude, last.longitude, fix.latitude, fix.longitude)
        val speedKmh = distance / (dtMs / 1000.0) * 3.6
        if (speedKmh > maxSpeedKmh) return Decision.REJECT_SPEED

        val fromAnchor = GeoUtils.distanceM(
            currentAnchor.latitude, currentAnchor.longitude, fix.latitude, fix.longitude,
        )
        if (fromAnchor > stationaryRadiusM) {
            lastAccepted = fix
            anchor = fix
            return Decision.ACCEPT
        }

        val stationaryForMs = fix.timeMs - currentAnchor.timeMs
        if (stationaryForMs >= stationaryThinAfterMs && dtMs < stationaryKeepIntervalMs) {
            return Decision.THINNED
        }
        lastAccepted = fix
        return Decision.ACCEPT
    }

    /** 記録の復元時に、最後に保存した点を基準として設定する。 */
    fun seed(fix: LocationFix) {
        lastAccepted = fix
        anchor = fix
    }

    /** 一時停止からの再開など、新しい区間を始めるときに呼ぶ。 */
    fun startNewSegment() {
        lastAccepted = null
        anchor = null
    }
}
