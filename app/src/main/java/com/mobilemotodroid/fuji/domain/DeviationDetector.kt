package com.mobilemotodroid.fuji.domain

/**
 * ルート逸脱の判定（FR-RTE-04, 05）。
 *
 * ルートまでの距離が [thresholdM] を超えた状態が [durationMs] 以上続いたら「逸脱」、
 * しきい値以内に戻ったら「復帰」とする。状態が変わったときだけイベントを返す。
 */
class DeviationDetector(
    var thresholdM: Double = 50.0,
    var durationMs: Long = 30_000L,
) {
    enum class Event { DEVIATED, RETURNED }

    var isDeviated: Boolean = false
        private set

    private var offRouteSinceMs: Long? = null

    fun update(distanceToRouteM: Double, timeMs: Long): Event? {
        if (distanceToRouteM > thresholdM) {
            val since = offRouteSinceMs ?: timeMs.also { offRouteSinceMs = it }
            if (!isDeviated && timeMs - since >= durationMs) {
                isDeviated = true
                return Event.DEVIATED
            }
            return null
        }
        offRouteSinceMs = null
        if (isDeviated) {
            isDeviated = false
            return Event.RETURNED
        }
        return null
    }

    fun reset() {
        isDeviated = false
        offRouteSinceMs = null
    }
}
