package com.example.traillog.service

import android.location.Location
import com.example.traillog.domain.GeoPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class RecordingPhase { IDLE, RECORDING, PAUSED }

/** 記録中のリアルタイム情報（F4）。 */
data class RecordingState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val activityId: Long? = null,
    /** 一時停止までに積み上げた経過時間（ミリ秒）。 */
    val movingTimeBaseMs: Long = 0,
    /** 記録中なら、直近で開始・再開した時刻。 */
    val resumedAtMs: Long? = null,
    val distanceM: Double = 0.0,
    val ascentM: Double = 0.0,
    val descentM: Double = 0.0,
    val altitudeM: Double? = null,
    val batteryPercent: Int? = null,
    val lastLocation: Location? = null,
    val hasRoute: Boolean = false,
    val distanceToRouteM: Double? = null,
    val isOffRoute: Boolean = false,
    val hasBarometer: Boolean = false,
) {
    /** FR-STA-01：一時停止中を除いた経過時間。 */
    fun movingTimeMs(nowMs: Long): Long = movingTimeBaseMs + (resumedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)
}

/**
 * 記録サービスと画面の間で状態を共有する。
 * 軌跡は区間（一時停止で区切られた部分）ごとの点のリストで持つ（FR-STA-06 の描画用）。
 */
@Singleton
class RecordingStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _track = MutableStateFlow<List<List<GeoPoint>>>(emptyList())
    val track: StateFlow<List<List<GeoPoint>>> = _track.asStateFlow()

    private val _finished = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 1)

    /** 記録を終了した活動の ID。画面はこれを受けて保存画面へ移る。 */
    val finished: SharedFlow<Long> = _finished.asSharedFlow()

    fun update(transform: (RecordingState) -> RecordingState) = _state.update(transform)

    fun setTrack(segments: List<List<GeoPoint>>) {
        _track.value = segments
    }

    fun appendPoint(point: GeoPoint, newSegment: Boolean) = _track.update { segments ->
        if (newSegment || segments.isEmpty()) segments + listOf(listOf(point))
        else segments.dropLast(1) + listOf(segments.last() + point)
    }

    fun markFinished(activityId: Long) {
        _state.value = RecordingState()
        _track.value = emptyList()
        _finished.tryEmit(activityId)
    }

    /** 保存画面に移ったら呼び、同じイベントを二度処理しないようにする。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeFinished() {
        _finished.resetReplayCache()
    }

    fun reset() {
        _state.value = RecordingState()
        _track.value = emptyList()
    }
}
