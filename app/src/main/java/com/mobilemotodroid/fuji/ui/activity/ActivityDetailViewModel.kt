package com.mobilemotodroid.fuji.ui.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemotodroid.fuji.data.db.ActivityEntity
import com.mobilemotodroid.fuji.data.db.PhotoEntity
import com.mobilemotodroid.fuji.data.db.TrackPointEntity
import com.mobilemotodroid.fuji.data.repository.ActivityRepository
import com.mobilemotodroid.fuji.data.settings.SettingsRepository
import com.mobilemotodroid.fuji.domain.GeoPoint
import com.mobilemotodroid.fuji.domain.GeoUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 活動詳細の地図とグラフに使うデータ。 */
data class TrackData(
    val segments: List<List<GeoPoint>>,
    val profile: ElevationProfile,
) {
    val allPoints: List<GeoPoint> get() = segments.flatten()
}

@HiltViewModel
class ActivityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ActivityRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val activityId: Long = checkNotNull(savedStateHandle.get<Long>("activityId"))

    val activity: StateFlow<ActivityEntity?> =
        repository.observe(activityId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val photos: StateFlow<List<PhotoEntity>> =
        repository.observePhotos(activityId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings = settingsRepository.settings

    private val _track = MutableStateFlow<TrackData?>(null)
    val track: StateFlow<TrackData?> = _track.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val points = repository.getPoints(activityId)
            _track.value = withContext(Dispatchers.Default) { buildTrackData(points) }
        }
    }

    fun updateTitleMemo(title: String, memo: String) = viewModelScope.launch {
        repository.updateTitleMemo(activityId, title, memo)
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        repository.delete(activityId)
        onDeleted()
    }

    /** FR-ACT-09 */
    fun exportGpx(onReady: (File) -> Unit) = viewModelScope.launch {
        runCatching { repository.exportGpx(activityId) }
            .onSuccess(onReady)
            .onFailure { _messages.tryEmit("GPX を書き出せませんでした") }
    }

    /** FR-ACT-10 */
    fun saveAsRoute() = viewModelScope.launch {
        runCatching { repository.saveAsRoute(activityId) }
            .onSuccess { _messages.tryEmit("ルート一覧に保存しました") }
            .onFailure { _messages.tryEmit(it.message ?: "ルートにできませんでした") }
    }

    companion object {
        /** NFR-PRF-03：グラフの点数を抑えて、長い記録でもすぐ表示できるようにする。 */
        private const val MAX_PROFILE_POINTS = 500

        fun buildTrackData(points: List<TrackPointEntity>): TrackData {
            val segments = points.groupBy { it.segment }.toSortedMap().values
                .map { seg -> seg.map { GeoPoint(it.latitude, it.longitude) } }

            val byDistance = ArrayList<ProfilePoint>()
            val byTime = ArrayList<ProfilePoint>()
            val startTime = points.firstOrNull()?.time ?: 0L
            var distance = 0.0
            var prev: TrackPointEntity? = null
            for (p in points) {
                val last = prev
                if (last != null && last.segment == p.segment) {
                    distance += GeoUtils.distanceM(last.latitude, last.longitude, p.latitude, p.longitude)
                }
                prev = p
                val alt = p.altitude ?: continue
                byDistance += ProfilePoint(distance.toFloat(), alt.toFloat())
                byTime += ProfilePoint(((p.time - startTime) / 1000f), alt.toFloat())
            }
            return TrackData(segments, ElevationProfile(byDistance.downsample(), byTime.downsample()))
        }

        private fun List<ProfilePoint>.downsample(): List<ProfilePoint> {
            if (size <= MAX_PROFILE_POINTS) return this
            val step = size.toDouble() / MAX_PROFILE_POINTS
            val result = (0 until MAX_PROFILE_POINTS).map { this[(it * step).toInt()] }.toMutableList()
            if (result.last() != last()) result += last()
            return result
        }
    }
}
