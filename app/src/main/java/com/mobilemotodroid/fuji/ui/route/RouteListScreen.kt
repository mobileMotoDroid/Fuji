package com.mobilemotodroid.fuji.ui.route

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobilemotodroid.fuji.data.db.RouteEntity
import com.mobilemotodroid.fuji.data.gpx.GpxParseException
import com.mobilemotodroid.fuji.data.repository.RouteRepository
import com.mobilemotodroid.fuji.data.settings.AppSettings
import com.mobilemotodroid.fuji.data.settings.SettingsRepository
import com.mobilemotodroid.fuji.service.RecordingPhase
import com.mobilemotodroid.fuji.service.RecordingStateHolder
import com.mobilemotodroid.fuji.ui.common.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteListViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val settingsRepository: SettingsRepository,
    private val recordingState: RecordingStateHolder,
) : ViewModel() {
    val routes: StateFlow<List<RouteEntity>?> =
        routeRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** FR-RTE-01 / FR-RTE-07 */
    fun importGpx(uri: Uri) = viewModelScope.launch {
        try {
            val id = routeRepository.importGpx(uri)
            settingsRepository.setSelectedRouteId(id)
            _messages.tryEmit("ルートを読み込み、地図に表示しました")
        } catch (e: GpxParseException) {
            _messages.tryEmit(e.message ?: "GPX を読み込めませんでした")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _messages.tryEmit("GPX を読み込めませんでした")
        }
    }

    /** FR-RTE-03：地図に表示するルートを選ぶ。 */
    fun select(id: Long?) = viewModelScope.launch {
        if (recordingState.state.value.phase != RecordingPhase.IDLE) {
            _messages.tryEmit("記録中はルートを変更できません")
            return@launch
        }
        settingsRepository.setSelectedRouteId(id)
    }

    fun delete(route: RouteEntity) = viewModelScope.launch {
        if (settings.value.selectedRouteId == route.id) settingsRepository.setSelectedRouteId(null)
        routeRepository.delete(route.id)
    }
}

/** SCR-07：ルート一覧（F5）。 */
@Composable
fun RouteListScreen(
    onShowOnMap: () -> Unit,
    onDownloadAround: (Long) -> Unit,
    viewModel: RouteListViewModel = hiltViewModel(),
) {
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleting by remember { mutableStateOf<RouteEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importGpx(uri)
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ルート") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(GPX_MIME_TYPES) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("GPX を読み込む") },
            )
        },
    ) { padding ->
        val list = routes
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "予定ルートの GPX ファイルを読み込むと、地図に表示し、ルートから外れたときに警告します。",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { route ->
                    val selected = settings.selectedRouteId == route.id
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selected) {
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        } else {
                            CardDefaults.cardColors()
                        },
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "地図に表示中",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                Text(route.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("${Format.distance(route.distanceM)}・${Format.date(route.importedAt)} に読み込み")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (selected) {
                                    OutlinedButton(onClick = { viewModel.select(null) }) { Text("表示をやめる") }
                                } else {
                                    FilledTonalButton(onClick = {
                                        viewModel.select(route.id)
                                        onShowOnMap()
                                    }) { Text("地図に表示") }
                                }
                                OutlinedButton(onClick = { onDownloadAround(route.id) }) { Text("周辺の地図を保存") }
                                TextButton(onClick = { deleting = route }) { Text("削除") }
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { route ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("ルートを削除しますか？") },
            text = { Text("「${route.name}」を削除します。ダウンロード済みの地図は削除されません。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(route)
                    deleting = null
                }) { Text("削除する") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } },
        )
    }
}

/** GPX は端末やアプリによって MIME タイプがまちまちなので、広めに受け付ける。 */
private val GPX_MIME_TYPES = arrayOf(
    "application/gpx+xml",
    "application/xml",
    "text/xml",
    "application/octet-stream",
    "*/*",
)
