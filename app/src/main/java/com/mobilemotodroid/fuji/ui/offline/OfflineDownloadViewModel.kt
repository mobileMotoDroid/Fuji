package com.mobilemotodroid.fuji.ui.offline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilemotodroid.fuji.data.map.DownloadArea
import com.mobilemotodroid.fuji.data.map.DownloadEstimate
import com.mobilemotodroid.fuji.data.map.MapStyles
import com.mobilemotodroid.fuji.data.map.OfflineMapRepository
import com.mobilemotodroid.fuji.data.repository.RouteRepository
import com.mobilemotodroid.fuji.data.repository.RouteWithPoints
import com.mobilemotodroid.fuji.data.settings.AppSettings
import com.mobilemotodroid.fuji.data.settings.SettingsRepository
import com.mobilemotodroid.fuji.domain.GeoBounds
import com.mobilemotodroid.fuji.ui.common.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DownloadUiState(
    val name: String = "",
    val minTileZoom: Int = 10,
    val maxTileZoom: Int = 16,
    /** ルート周辺モードのときの幅（ルートの両側それぞれの距離）。 */
    val corridorWidthM: Int = 500,
    /** 矩形モードのときに地図の枠から求めた範囲。 */
    val bounds: GeoBounds? = null,
    val estimate: DownloadEstimate? = null,
    val isStarting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class OfflineDownloadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val offlineRepository: OfflineMapRepository,
    private val routeRepository: RouteRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val routeId: Long? = savedStateHandle.get<Long>("routeId")?.takeIf { it > 0 }

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _route = MutableStateFlow<RouteWithPoints?>(null)
    val route: StateFlow<RouteWithPoints?> = _route.asStateFlow()

    private val _state = MutableStateFlow(DownloadUiState(name = "地図 ${Format.date(System.currentTimeMillis())}"))
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    val isRouteMode: Boolean get() = routeId != null

    private var estimateJob: Job? = null

    init {
        routeId?.let { id ->
            viewModelScope.launch {
                val r = routeRepository.getWithPoints(id)
                _route.value = r
                if (r != null) _state.update { it.copy(name = "${r.route.name} 周辺") }
                recompute()
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }

    fun setZoomRange(min: Int, max: Int) {
        _state.update { it.copy(minTileZoom = min, maxTileZoom = max) }
        recompute()
    }

    fun setCorridorWidth(value: Int) {
        _state.update { it.copy(corridorWidthM = value) }
        recompute()
    }

    fun setBounds(bounds: GeoBounds) {
        if (isRouteMode) return
        _state.update { it.copy(bounds = bounds) }
        recompute()
    }

    private fun currentArea(): DownloadArea? {
        val s = _state.value
        return if (isRouteMode) {
            _route.value?.points?.takeIf { it.size >= 2 }?.let { DownloadArea.RouteCorridor(it, s.corridorWidthM.toDouble()) }
        } else {
            s.bounds?.let { DownloadArea.Rectangle(it) }
        }
    }

    /** FR-OFF-02：推定タイル数と容量を計算し直す（操作が落ち着いてから）。 */
    private fun recompute() {
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            delay(250)
            val area = currentArea() ?: return@launch
            val s = _state.value
            val estimate = withContext(Dispatchers.Default) {
                offlineRepository.estimate(area, s.minTileZoom, s.maxTileZoom, settings.value.mapType)
            }
            _state.update { it.copy(estimate = estimate) }
        }
    }

    fun download(onStarted: () -> Unit) {
        val area = currentArea() ?: return
        val s = _state.value
        if (s.isStarting) return
        _state.update { it.copy(isStarting = true, error = null) }
        viewModelScope.launch {
            runCatching {
                offlineRepository.createAndStart(
                    name = s.name.ifBlank { "地図" },
                    area = area,
                    minTileZoom = s.minTileZoom,
                    maxTileZoom = s.maxTileZoom,
                    mapType = settings.value.mapType,
                )
            }.onSuccess {
                onStarted()
            }.onFailure { e ->
                _state.update { it.copy(isStarting = false, error = e.message ?: "ダウンロードを開始できませんでした") }
            }
        }
    }

    companion object {
        val TILE_ZOOM_RANGE = 8..MapStyles.MAX_TILE_ZOOM
    }
}
