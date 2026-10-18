package com.mobilemotodroid.fuji.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.mobilemotodroid.fuji.data.map.OfflineMapRepository
import com.mobilemotodroid.fuji.data.map.OfflineRegionInfo
import com.mobilemotodroid.fuji.ui.common.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OfflineListViewModel @Inject constructor(
    private val repository: OfflineMapRepository,
) : ViewModel() {
    /** 画面を表示している間、進捗を定期的に読み直す。 */
    val regions: StateFlow<List<OfflineRegionInfo>?> = flow {
        while (true) {
            emit(runCatching { repository.list() }.getOrDefault(emptyList()))
            delay(1_500)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), null)

    fun pause(id: Long) = viewModelScope.launch { repository.pause(id) }
    fun resume(id: Long) = repository.start(id)
    fun delete(id: Long) = viewModelScope.launch { runCatching { repository.delete(id) } }
}

/** SCR-08：オフライン地図一覧（FR-OFF-05, 07, 08）。 */
@Composable
fun OfflineListScreen(onNewDownload: () -> Unit, viewModel: OfflineListViewModel = hiltViewModel()) {
    val regions by viewModel.regions.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<OfflineRegionInfo?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("オフライン地図") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewDownload,
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                text = { Text("地図をダウンロード") },
            )
        },
    ) { padding ->
        val list = regions
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "山の中は圏外のことが多いため、出発前に地図をダウンロードしておきましょう。",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "合計 ${Format.bytes(list.sumOf { it.completedBytes })}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                items(list, key = { it.id }) { region ->
                    RegionCard(
                        region = region,
                        onPause = { viewModel.pause(region.id) },
                        onResume = { viewModel.resume(region.id) },
                        onDelete = { deleting = region },
                    )
                }
            }
        }
    }

    deleting?.let { region ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("地図を削除しますか？") },
            text = { Text("「${region.name}」（${Format.bytes(region.completedBytes)}）を削除します。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(region.id)
                    deleting = null
                }) { Text("削除する") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun RegionCard(region: OfflineRegionInfo, onPause: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(region.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${region.mapType.label}・ズーム ${region.minTileZoom}〜${region.maxTileZoom}・" +
                    (if (region.createdAt > 0) Format.date(region.createdAt) else ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            region.bounds?.let {
                Text(
                    "範囲 北緯 %.3f〜%.3f / 東経 %.3f〜%.3f".format(it.south, it.north, it.west, it.east),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            val status = when {
                region.isComplete -> "保存済み・${Format.bytes(region.completedBytes)}"
                region.isDownloading -> "ダウンロード中 ${(region.progress * 100).toInt()}%・${Format.bytes(region.completedBytes)}"
                else -> "中断中 ${(region.progress * 100).toInt()}%・${Format.bytes(region.completedBytes)}"
            }
            Text(status, style = MaterialTheme.typography.labelLarge)
            if (!region.isComplete) {
                LinearProgressIndicator(progress = { region.progress }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!region.isComplete) {
                    if (region.isDownloading) {
                        FilledTonalButton(onClick = onPause) { Text("中断") }
                    } else {
                        FilledTonalButton(onClick = onResume) { Text("再開") }
                    }
                }
                TextButton(onClick = onDelete) { Text("削除") }
            }
        }
    }
}
