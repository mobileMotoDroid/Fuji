package com.mobilemotodroid.fuji.domain

/**
 * 累積の登り・下りを計算する（FR-STA-05）。
 *
 * 基準の標高から [thresholdM] 以上上下したときだけ加算し、基準を更新する（ヒステリシス）。
 * これにより、GPS や気圧の細かな揺らぎで獲得標高が過大になるのを防ぐ。
 */
class ElevationGainCalculator(private val thresholdM: Double = DEFAULT_THRESHOLD_M) {
    var ascentM: Double = 0.0
        private set
    var descentM: Double = 0.0
        private set

    private var reference: Double? = null

    fun add(altitudeM: Double) {
        val ref = reference
        if (ref == null) {
            reference = altitudeM
            return
        }
        val diff = altitudeM - ref
        if (diff >= thresholdM) {
            ascentM += diff
            reference = altitudeM
        } else if (diff <= -thresholdM) {
            descentM += -diff
            reference = altitudeM
        }
    }

    companion object {
        const val DEFAULT_THRESHOLD_M = 5.0
    }
}
