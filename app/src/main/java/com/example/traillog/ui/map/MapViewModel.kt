package com.example.traillog.ui.map

import android.location.Location
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traillog.data.db.ActivityEntity
import com.example.traillog.data.location.ForegroundLocationSource
import com.example.traillog.data.repository.ActivityRepository
import com.example.traillog.data.repository.RouteRepository
import com.example.traillog.data.repository.RouteWithPoints
import com.example.traillog.data.settings.AppSettings
import com.example.traillog.data.settings.MapType
import com.example.traillog.data.settings.OrientationMode
import com.example.traillog.data.settings.SettingsRepository
import com.example.traillog.domain.GeoPoint
import com.example.traillog.service.RecordingController
import com.example.traillog.service.RecordingPhase
import com.example.traillog.service.RecordingStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** 地図（ホーム）画面と記録中画面（SCR-02, SCR-03）の ViewModel。 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val routeRepository: RouteRepository,
    private val activityRepository: ActivityRepository,
    private val recordingController: RecordingController,
    private val stateHolder: RecordingStateHolder,
    locationSource: ForegroundLocationSource,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val recording = stateHolder.state
    val track = stateHolder.track
    val finishedActivity: SharedFlow<Long> = stateHolder.finished

    val selectedRoute: StateFlow<RouteWithPoints?> = settingsRepository.settings
        .map { it.selectedRouteId }
        .distinctUntilChanged()
        .map { id -> id?.let { routeRepository.getWithPoints(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 画面表示中だけ取得する現在地。権限を確認してから収集すること。 */
    val foregroundLocation: Flow<Location> = locationSource.locations()

    private val _unfinished = MutableStateFlow<ActivityEntity?>(null)

    /** FR-REC-05：前回、終了せずに残っている記録。 */
    val unfinished: StateFlow<ActivityEntity?> = _unfinished.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            if (stateHolder.state.value.phase == RecordingPhase.IDLE) {
                _unfinished.value = activityRepository.findUnfinished()
            }
        }
    }

    fun startRecording() = recordingController.start()
    fun pauseRecording() = recordingController.pause()
    fun resumeRecording() = recordingController.resume()
    fun stopRecording() = recordingController.stop()

    fun consumeFinished() = stateHolder.consumeFinished()

    fun restoreUnfinished() {
        _unfinished.value = null
        recordingController.restore()
    }

    fun finishUnfinished(onFinished: (Long) -> Unit) {
        val activity = _unfinished.value ?: return
        _unfinished.value = null
        viewModelScope.launch {
            activityRepository.finish(activity.id, System.currentTimeMillis())
            onFinished(activity.id)
        }
    }

    fun toggleMapType() = viewModelScope.launch {
        val next = if (settings.value.mapType == MapType.STANDARD) MapType.PHOTO else MapType.STANDARD
        settingsRepository.setMapType(next)
    }

    fun toggleOrientation() = viewModelScope.launch {
        val next = if (settings.value.orientationMode == OrientationMode.NORTH_UP) OrientationMode.HEADING_UP else OrientationMode.NORTH_UP
        settingsRepository.setOrientationMode(next)
    }

    fun markBatteryGuideShown() = viewModelScope.launch { settingsRepository.setBatteryGuideShown() }

    fun clearSelectedRoute() = viewModelScope.launch { settingsRepository.setSelectedRouteId(null) }

    fun newPhotoFile(): File = activityRepository.newPhotoFile()

    /** FR-ACT-05：カメラで撮った写真を、撮影時の位置とともに保存する。 */
    fun addCapturedPhoto(file: File, location: Location?) {
        val activityId = recording.value.activityId ?: return
        viewModelScope.launch {
            activityRepository.addPhoto(activityId, file, System.currentTimeMillis(), location?.toGeoPoint())
            _messages.tryEmit("写真を保存しました")
        }
    }

    fun addPickedPhoto(uri: Uri, location: Location?) {
        val activityId = recording.value.activityId ?: return
        viewModelScope.launch {
            runCatching { activityRepository.importPhoto(activityId, uri, location?.toGeoPoint()) }
                .onSuccess { _messages.tryEmit("写真を保存しました") }
                .onFailure { _messages.tryEmit("写真を保存できませんでした") }
        }
    }

    fun showMessage(message: String) {
        _messages.tryEmit(message)
    }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)
}
