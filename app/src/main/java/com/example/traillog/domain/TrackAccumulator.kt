package com.example.traillog.domain

/** 統計計算に使う軌跡の 1 点。 */
data class TrackSample(
    val timeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val segment: Int,
)

/**
 * 軌跡の点を順に受け取り、距離・累積標高・最高／最低標高を計算する。
 * 区間（segment）が変わった点の間の距離は加算しない（一時停止中の移動を含めないため）。
 */
class TrackAccumulator(gainThresholdM: Double = ElevationGainCalculator.DEFAULT_THRESHOLD_M) {
    private val gain = ElevationGainCalculator(gainThresholdM)

    var lastSample: TrackSample? = null
        private set
    var distanceM: Double = 0.0
        private set
    var maxAltitudeM: Double? = null
        private set
    var minAltitudeM: Double? = null
        private set
    var pointCount: Int = 0
        private set

    val ascentM: Double get() = gain.ascentM
    val descentM: Double get() = gain.descentM

    fun add(sample: TrackSample) {
        val last = lastSample
        if (last != null && last.segment == sample.segment) {
            distanceM += GeoUtils.distanceM(last.latitude, last.longitude, sample.latitude, sample.longitude)
        }
        sample.altitudeM?.let { alt ->
            gain.add(alt)
            maxAltitudeM = maxAltitudeM?.let { maxOf(it, alt) } ?: alt
            minAltitudeM = minAltitudeM?.let { minOf(it, alt) } ?: alt
        }
        lastSample = sample
        pointCount++
    }

    fun addAll(samples: Iterable<TrackSample>) = samples.forEach(::add)
}
