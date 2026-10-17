package com.example.traillog.ui.offline

import android.graphics.PointF
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.traillog.data.map.MapStyles
import com.example.traillog.data.map.OfflineMapRepository
import com.example.traillog.domain.GeoBounds
import com.example.traillog.ui.common.Format
import com.example.traillog.ui.common.JAPAN_CENTER
import com.example.traillog.ui.common.MapAttribution
import com.example.traillog.ui.common.MapHandle
import com.example.traillog.ui.common.MapLayers
import com.example.traillog.ui.common.MapLibreView
import com.example.traillog.ui.common.fitTo
import org.maplibre.android.camera.CameraUpdateFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 地図の枠の内側の余白。 */
private val FRAME_INSET = 24.dp

/** SCR-09：地図のダウンロード（FR-OFF-01〜04）。 */
@Composable
fun OfflineDownloadScreen(
    onBack: () -> Unit,
    onStarted: () -> Unit,
    viewModel: OfflineDownloadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val route by viewModel.route.collectAsStateWithLifecycle()
    val insetPx = with(LocalDensity.current) { FRAME_INSET.toPx() }
    var handle by remember { mutableStateOf<MapHandle?>(null) }

    LaunchedEffect(handle, route) {
        val h = handle ?: return@LaunchedEffect
        val points = route?.points.orEmpty()
        MapLayers.setRoute(h.style, points)
        if (points.isNotEmpty()) h.fitTo(points, paddingPx = 100)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("地図をダウンロード") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                MapLibreView(styleUri = MapStyles.styleUri(settings.mapType), modifier = Modifier.fillMaxSize()) { h ->
                    if (handle?.map !== h.map) {
                        if (h.map.cameraPosition.zoom < 2.0) {
                            h.map.moveCamera(CameraUpdateFactory.newLatLngZoom(JAPAN_CENTER, 9.0))
                        }
                        // 枠の四隅の座標から、ダウンロードする矩形を求める
                        val updateBounds = {
                            val view = h.map
                            val width = view.width
                            val height = view.height
                            if (width > insetPx * 2 && height > insetPx * 2) {
                                val nw = view.projection.fromScreenLocation(PointF(insetPx, insetPx))
                                val se = view.projection.fromScreenLocation(PointF(width - insetPx, height - insetPx))
                                viewModel.setBounds(
                                    GeoBounds(
                                        south = min(nw.latitude, se.latitude),
                                        west = min(nw.longitude, se.longitude),
                                        north = max(nw.latitude, se.latitude),
                                        east = max(nw.longitude, se.longitude),
                                    ),
                                )
                            }
                        }
                        h.map.addOnCameraIdleListener { updateBounds() }
                        updateBounds()
                    }
                    handle = h
                }
                if (!viewModel.isRouteMode) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(FRAME_INSET)
                            .border(3.dp, Color(0xFFE53935)),
                    )
                }
                MapAttribution(Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(8.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (viewModel.isRouteMode) "ルートの周辺をダウンロードします。" else "赤い枠の範囲をダウンロードします。地図を動かして範囲を決めてください。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("名前") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                val zoomRange = OfflineDownloadViewModel.TILE_ZOOM_RANGE
                Text("ズーム ${state.minTileZoom}〜${state.maxTileZoom}（大きいほど詳細・容量が増えます）")
                RangeSlider(
                    value = state.minTileZoom.toFloat()..state.maxTileZoom.toFloat(),
                    onValueChange = { r -> viewModel.setZoomRange(r.start.roundToInt(), r.endInclusive.roundToInt()) },
                    valueRange = zoomRange.first.toFloat()..zoomRange.last.toFloat(),
                    steps = zoomRange.last - zoomRange.first - 1,
                )

                if (viewModel.isRouteMode) {
                    Text("ルートからの幅 ${state.corridorWidthM} m")
                    Slider(
                        value = state.corridorWidthM.toFloat(),
                        onValueChange = { viewModel.setCorridorWidth((it / 100).roundToInt() * 100) },
                        valueRange = 100f..2_000f,
                        steps = 18,
                    )
                }

                state.estimate?.let { e ->
                    Text(
                        "推定 ${"%,d".format(e.tileCount)} 枚・約 ${Format.bytes(e.bytes)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (e.exceedsLimit) {
                        Text(
                            "範囲が広すぎます（上限 ${"%,d".format(OfflineMapRepository.MAX_TILES)} 枚）。範囲を狭めるか、ズームの上限を下げてください。",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "地理院タイルの提供元に負荷をかけないよう、必要な範囲だけをダウンロードしてください。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { viewModel.download(onStarted) },
                    enabled = state.estimate?.exceedsLimit == false && !state.isStarting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (state.isStarting) "準備中…" else "ダウンロード開始") }
            }
        }
    }
}
