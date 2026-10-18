package com.mobilemotodroid.fuji.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemotodroid.fuji.data.map.MapStyles
import com.mobilemotodroid.fuji.data.settings.AppSettings
import com.mobilemotodroid.fuji.domain.GeoPoint
import com.mobilemotodroid.fuji.ui.common.Format
import com.mobilemotodroid.fuji.ui.common.Intents
import com.mobilemotodroid.fuji.ui.common.MapAttribution
import com.mobilemotodroid.fuji.ui.common.MapHandle
import com.mobilemotodroid.fuji.ui.common.MapLayers
import com.mobilemotodroid.fuji.ui.common.MapLibreView
import com.mobilemotodroid.fuji.ui.common.PhotoThumbnail
import com.mobilemotodroid.fuji.ui.common.PhotoViewerDialog
import com.mobilemotodroid.fuji.ui.common.fitTo
import org.maplibre.android.maps.MapLibreMap

/** SCR-06：活動詳細（F6）。 */
@Composable
fun ActivityDetailScreen(
    onBack: () -> Unit,
    viewModel: ActivityDetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val track by viewModel.track.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val snackbarHostState = remember { SnackbarHostState() }

    var handle by remember { mutableStateOf<MapHandle?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var viewingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    // FR-ACT-02 / FR-ACT-06：軌跡と写真のピンを地図に表示する
    LaunchedEffect(handle, track) {
        val h = handle ?: return@LaunchedEffect
        val t = track ?: return@LaunchedEffect
        MapLayers.setTrack(h.style, t.segments)
        h.fitTo(t.allPoints, paddingPx = 80)
    }
    LaunchedEffect(handle, photos) {
        val h = handle ?: return@LaunchedEffect
        MapLayers.setPhotos(
            h.style,
            photos.mapNotNull { p ->
                if (p.latitude != null && p.longitude != null) p.id to GeoPoint(p.latitude, p.longitude) else null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activity?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.exportGpx { file -> Intents.shareFile(context, file, "application/gpx+xml", "GPX を共有") }
                    }) { Icon(Icons.Default.Share, contentDescription = "GPX で書き出し・共有") }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "その他") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("タイトル・メモを編集") }, onClick = { showMenu = false; showEdit = true })
                            DropdownMenuItem(text = { Text("ルートとして保存") }, onClick = { showMenu = false; viewModel.saveAsRoute() })
                            DropdownMenuItem(text = { Text("削除") }, onClick = { showMenu = false; confirmDelete = true })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                MapLibreView(styleUri = MapStyles.styleUri(settings.mapType), modifier = Modifier.fillMaxSize()) { h ->
                    if (handle?.map !== h.map) {
                        h.map.addOnMapClickListener(photoClickListener(h.map) { id ->
                            viewingPhotoPath = photos.firstOrNull { it.id == id }?.filePath
                        })
                    }
                    handle = h
                }
                MapAttribution(Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(8.dp))
            }

            val a = activity
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (a != null) {
                    item {
                        Text(
                            "${Format.dateTime(a.startedAt)} 〜 ${a.endedAt?.let { Format.dateTime(it) } ?: ""}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    item { StatsGrid(a) }
                }
                track?.let { t -> item { ElevationChart(t.profile) } }
                if (a != null && a.memo.isNotBlank()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("メモ", style = MaterialTheme.typography.titleMedium)
                                Text(a.memo, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                if (photos.isNotEmpty()) {
                    item {
                        Text("写真（${photos.size}枚）", style = MaterialTheme.typography.titleMedium)
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(photos, key = { it.id }) { photo ->
                                PhotoThumbnail(
                                    photo.filePath,
                                    Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewingPhotoPath = photo.filePath },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    viewingPhotoPath?.let { path -> PhotoViewerDialog(path, onDismiss = { viewingPhotoPath = null }) }

    val current = activity
    if (showEdit && current != null) {
        var title by rememberSaveable { mutableStateOf(current.title) }
        var memo by rememberSaveable { mutableStateOf(current.memo) }
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("タイトル・メモを編集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("タイトル") }, singleLine = true)
                    OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("メモ") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateTitleMemo(title, memo)
                    showEdit = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("キャンセル") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("この記録を削除しますか？") },
            text = { Text("軌跡と写真がすべて削除され、元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("削除する") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("キャンセル") } },
        )
    }
}

/** 地図上の写真のピンをタップしたら、その写真の ID を返す。 */
private fun photoClickListener(map: MapLibreMap, onPhoto: (Long) -> Unit) = MapLibreMap.OnMapClickListener { latLng ->
    val screen = map.projection.toScreenLocation(latLng)
    val feature = map.queryRenderedFeatures(screen, MapLayers.PHOTO_LAYER).firstOrNull()
    if (feature != null && feature.hasProperty(MapLayers.PHOTO_ID)) {
        onPhoto(feature.getNumberProperty(MapLayers.PHOTO_ID).toLong())
        true
    } else {
        false
    }
}
