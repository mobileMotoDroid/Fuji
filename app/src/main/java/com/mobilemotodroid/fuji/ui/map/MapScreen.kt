package com.mobilemotodroid.fuji.ui.map

import android.Manifest
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilemotodroid.fuji.data.map.MapStyles
import com.mobilemotodroid.fuji.data.settings.MapType
import com.mobilemotodroid.fuji.data.settings.OrientationMode
import com.mobilemotodroid.fuji.domain.TileMath
import com.mobilemotodroid.fuji.service.RecordingPhase
import com.mobilemotodroid.fuji.ui.common.BatteryGuide
import com.mobilemotodroid.fuji.ui.common.Format
import com.mobilemotodroid.fuji.ui.common.Intents
import com.mobilemotodroid.fuji.ui.common.JAPAN_CENTER
import com.mobilemotodroid.fuji.ui.common.LOCATION_PERMISSIONS
import com.mobilemotodroid.fuji.ui.common.MapAttribution
import com.mobilemotodroid.fuji.ui.common.MapHandle
import com.mobilemotodroid.fuji.ui.common.MapLabel
import com.mobilemotodroid.fuji.ui.common.MapLayers
import com.mobilemotodroid.fuji.ui.common.MapLibreView
import com.mobilemotodroid.fuji.ui.common.ScaleBar
import com.mobilemotodroid.fuji.ui.common.enableLocationComponent
import com.mobilemotodroid.fuji.ui.common.fitTo
import com.mobilemotodroid.fuji.ui.common.followLocation
import com.mobilemotodroid.fuji.ui.common.hasLocationPermission
import com.mobilemotodroid.fuji.ui.common.needsNotificationPermission
import com.mobilemotodroid.fuji.ui.common.rememberIsOnline
import com.mobilemotodroid.fuji.ui.common.updateLocation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.OnCameraTrackingChangedListener
import java.io.File

/** 記録を始めるまでの手順（権限の確認と、初回のバッテリー設定の案内）。 */
private enum class StartStep {
    NONE, LOCATION_RATIONALE, REQUEST_LOCATION, REQUEST_NOTIFICATION, CHECK_BATTERY_GUIDE, BATTERY_GUIDE, START
}

/** 計画書の提出サービス（FR-SAF-03）。 */
private const val COMPASS_URL = "https://www.mt-compass.com/"

/**
 * SCR-02 地図（ホーム）と SCR-03 記録中を兼ねる画面。
 * 記録開始はこの画面のボタン 1 回で行える（NFR-USA-01）。
 */
@Composable
fun MapScreen(
    onRecordingFinished: (Long) -> Unit,
    onOpenRoutes: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val track by viewModel.track.collectAsStateWithLifecycle()
    val selectedRoute by viewModel.selectedRoute.collectAsStateWithLifecycle()
    val unfinished by viewModel.unfinished.collectAsStateWithLifecycle()
    val isOnline = rememberIsOnline()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasLocationPermission = context.hasLocationPermission() }

    val locationFlow = remember(hasLocationPermission) {
        if (hasLocationPermission) viewModel.foregroundLocation else emptyFlow()
    }
    val foregroundLocation by locationFlow.collectAsStateWithLifecycle(initialValue = null)
    val location: Location? = foregroundLocation ?: recording.lastLocation

    var handle by remember { mutableStateOf<MapHandle?>(null) }
    var metersPerDp by remember { mutableStateOf<Double?>(null) }
    var following by rememberSaveable { mutableStateOf(true) }
    var startStep by remember { mutableStateOf(StartStep.NONE) }
    var resumeAfterPermission by remember { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }
    var showShareMenu by remember { mutableStateOf(false) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    val headingUp = settings.orientationMode == OrientationMode.HEADING_UP
    val phase = recording.phase

    // ---- 権限・外部画面の起動 ----

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = context.hasLocationPermission()
        when {
            !hasLocationPermission -> {
                startStep = StartStep.NONE
                resumeAfterPermission = false
                viewModel.showMessage("位置情報の権限がないため、現在地の表示と記録ができません")
            }
            resumeAfterPermission -> {
                resumeAfterPermission = false
                viewModel.restoreUnfinished()
            }
            startStep == StartStep.REQUEST_LOCATION -> startStep = StartStep.REQUEST_NOTIFICATION
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { startStep = StartStep.CHECK_BATTERY_GUIDE }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (path != null) {
            if (success) viewModel.addCapturedPhoto(File(path), location) else File(path).delete()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addPickedPhoto(uri, location)
    }

    LaunchedEffect(startStep) {
        when (startStep) {
            StartStep.REQUEST_LOCATION -> locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
            StartStep.REQUEST_NOTIFICATION ->
                if (context.needsNotificationPermission()) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startStep = StartStep.CHECK_BATTERY_GUIDE
                }
            StartStep.CHECK_BATTERY_GUIDE ->
                startStep = if (settings.batteryGuideShown) StartStep.START else StartStep.BATTERY_GUIDE
            StartStep.START -> {
                viewModel.startRecording()
                following = true
                startStep = StartStep.NONE
            }
            else -> Unit
        }
    }

    fun requestStart() {
        startStep = if (hasLocationPermission) StartStep.REQUEST_NOTIFICATION else StartStep.LOCATION_RATIONALE
    }

    // ---- 画面外のイベント ----

    LaunchedEffect(Unit) {
        viewModel.finishedActivity.collect { id ->
            viewModel.consumeFinished()
            onRecordingFinished(id)
        }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // FR-SET-04：記録中は画面を消さない
    val view = LocalView.current
    val keepScreenOn = settings.keepScreenOn && phase != RecordingPhase.IDLE
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // ---- 地図の更新 ----

    DisposableEffect(handle, hasLocationPermission) {
        val h = handle
        val listener = object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {
                following = false
            }

            override fun onCameraTrackingChanged(currentMode: Int) = Unit
        }
        if (h != null && hasLocationPermission) {
            h.enableLocationComponent(context)
            h.map.locationComponent.addOnCameraTrackingChangedListener(listener)
        }
        onDispose {
            if (h != null) runCatching { h.map.locationComponent.removeOnCameraTrackingChangedListener(listener) }
        }
    }
    LaunchedEffect(handle, location) {
        val h = handle ?: return@LaunchedEffect
        location?.let { h.updateLocation(it) }
    }
    LaunchedEffect(handle, following, headingUp, location != null) {
        if (following && location != null) handle?.followLocation(headingUp)
    }
    LaunchedEffect(handle, track) {
        handle?.let { MapLayers.setTrack(it.style, track) }
    }
    LaunchedEffect(handle, selectedRoute) {
        val h = handle ?: return@LaunchedEffect
        val points = selectedRoute?.points.orEmpty()
        MapLayers.setRoute(h.style, points)
        // 現在地がまだ分からないときは、ルート全体が見えるようにする
        if (points.isNotEmpty() && location == null) {
            following = false
            h.fitTo(points, paddingPx = 120)
        }
    }

    Box(Modifier.fillMaxSize()) {
        MapLibreView(
            styleUri = MapStyles.styleUri(settings.mapType),
            modifier = Modifier.fillMaxSize(),
        ) { h ->
            val isNewMap = handle?.map !== h.map
            if (isNewMap) {
                if (h.map.cameraPosition.zoom < 2.0) {
                    h.map.moveCamera(CameraUpdateFactory.newLatLngZoom(JAPAN_CENTER, 5.0))
                }
                val updateScale = {
                    val cam = h.map.cameraPosition
                    metersPerDp = TileMath.metersPerDp(cam.target?.latitude ?: 0.0, cam.zoom)
                }
                h.map.addOnCameraMoveListener { updateScale() }
                updateScale()
            }
            handle = h
        }

        // ---- 上部：記録中パネル・警告 ----
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .padding(end = 64.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (phase != RecordingPhase.IDLE) {
                val now by produceState(System.currentTimeMillis(), phase) {
                    while (true) {
                        value = System.currentTimeMillis()
                        delay(1_000)
                    }
                }
                RecordingPanel(recording, now)
            }
            if (recording.isOffRoute) OffRouteBanner(recording.distanceToRouteM)
            if (!isOnline) MapLabel("オフライン：ダウンロード済みの範囲だけ表示します（未取得の範囲はグレー）")
            if (!hasLocationPermission) MapLabel("位置情報が許可されていないため、現在地を表示できません", emphasis = true)
            selectedRoute?.let { route ->
                AssistChip(
                    onClick = onOpenRoutes,
                    label = { Text("ルート：${route.route.name}", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Directions, contentDescription = null) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                if (phase == RecordingPhase.IDLE) {
                    TextButton(onClick = { viewModel.clearSelectedRoute() }) { Text("ルートの表示をやめる") }
                }
            }
        }

        // ---- 右側：地図の操作ボタン ----
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MapButton(
                icon = Icons.Default.Layers,
                description = if (settings.mapType == MapType.STANDARD) "航空写真に切り替え" else "標準地図に切り替え",
                onClick = { viewModel.toggleMapType() },
            )
            MapButton(
                icon = if (headingUp) Icons.Default.Navigation else Icons.Default.Explore,
                description = if (headingUp) "北を上にする" else "進行方向を上にする",
                onClick = {
                    viewModel.toggleOrientation()
                    following = true
                },
            )
            Box {
                MapButton(icon = Icons.Default.Share, description = "現在地を共有", onClick = { showShareMenu = true })
                DropdownMenu(expanded = showShareMenu, onDismissRequest = { showShareMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("現在地を共有") },
                        onClick = {
                            showShareMenu = false
                            location?.let { Intents.shareLocation(context, it.latitude, it.longitude) }
                                ?: viewModel.showMessage("現在地を取得しています")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("座標をコピー") },
                        onClick = {
                            showShareMenu = false
                            location?.let { Intents.copyCoordinates(context, it.latitude, it.longitude) }
                                ?: viewModel.showMessage("現在地を取得しています")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("登山計画書を提出（コンパス）") },
                        onClick = {
                            showShareMenu = false
                            Intents.openUrl(context, COMPASS_URL)
                        },
                    )
                }
            }
            MapButton(
                icon = Icons.Default.MyLocation,
                description = "現在地に戻る",
                highlighted = following,
                onClick = {
                    if (!hasLocationPermission) {
                        locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
                    } else {
                        following = true
                        handle?.followLocation(headingUp)
                    }
                },
            )
        }

        // ---- 下部：記録の操作・座標・縮尺・出典 ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            SnackbarHost(snackbarHostState)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                when (phase) {
                    RecordingPhase.IDLE -> BigButton("記録開始", Icons.Default.PlayArrow, onClick = { requestStart() })
                    RecordingPhase.RECORDING -> {
                        BigButton("一時停止", Icons.Default.Pause, tonal = true, onClick = { viewModel.pauseRecording() })
                        Box {
                            BigButton("写真", Icons.Default.PhotoCamera, tonal = true, onClick = { showPhotoMenu = true })
                            DropdownMenu(expanded = showPhotoMenu, onDismissRequest = { showPhotoMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("カメラで撮影") },
                                    onClick = {
                                        showPhotoMenu = false
                                        val file = viewModel.newPhotoFile()
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        pendingPhotoPath = file.absolutePath
                                        runCatching { takePicture.launch(uri) }.onFailure {
                                            pendingPhotoPath = null
                                            viewModel.showMessage("カメラアプリが見つかりません")
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("端末の写真から選ぶ") },
                                    onClick = {
                                        showPhotoMenu = false
                                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                )
                            }
                        }
                        BigButton("終了", Icons.Default.Stop, danger = true, onClick = { showStopConfirm = true })
                    }
                    RecordingPhase.PAUSED -> {
                        BigButton("再開", Icons.Default.PlayArrow, onClick = { viewModel.resumeRecording() })
                        BigButton("終了", Icons.Default.Stop, danger = true, onClick = { showStopConfirm = true })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                location?.let { MapLabel("現在地 ${Format.coordinates(it.latitude, it.longitude)}（±${it.accuracy.toInt()} m）") }
                ScaleBar(metersPerDp)
                MapAttribution()
            }
        }
    }

    // ---- ダイアログ ----

    if (startStep == StartStep.LOCATION_RATIONALE) {
        AlertDialog(
            onDismissRequest = { startStep = StartStep.NONE },
            title = { Text("位置情報の利用") },
            text = {
                Text(
                    "現在地の表示と GPS の記録に、正確な位置情報を使います。\n\n" +
                        "記録は端末の中にだけ保存され、自動で外部に送信されることはありません。" +
                        "記録中は通知を表示し、画面を消しても記録を続けます。",
                )
            },
            confirmButton = { TextButton(onClick = { startStep = StartStep.REQUEST_LOCATION }) { Text("許可する") } },
            dismissButton = { TextButton(onClick = { startStep = StartStep.NONE }) { Text("キャンセル") } },
        )
    }

    if (startStep == StartStep.BATTERY_GUIDE) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("記録が止まらないように") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("省電力機能によって、画面を消している間に記録が止まることがあります。このアプリをバッテリーの最適化の対象から外してください。")
                    Text(BatteryGuide.manufacturerAdvice(), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { Intents.openUrl(context, BatteryGuide.dontKillMyAppUrl()) }) {
                        Text("端末ごとの詳しい設定方法（英語）")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markBatteryGuideShown()
                    startStep = StartStep.START
                    Intents.openBatteryOptimizationSettings(context)
                }) { Text("開始して設定を開く") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.markBatteryGuideShown()
                    startStep = StartStep.START
                }) { Text("このまま開始") }
            },
        )
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("記録を終了しますか？") },
            text = { Text("終了すると、記録を保存する画面に移ります。") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    viewModel.stopRecording()
                }) { Text("終了する") }
            },
            dismissButton = { TextButton(onClick = { showStopConfirm = false }) { Text("続ける") } },
        )
    }

    unfinished?.let { activity ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("前回の記録が終了していません") },
            text = {
                Text(
                    "「${activity.title}」（${Format.dateTime(activity.startedAt)} 開始）の記録が、" +
                        "アプリの終了や端末の再起動で中断されています。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (hasLocationPermission) {
                        viewModel.restoreUnfinished()
                    } else {
                        resumeAfterPermission = true
                        locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
                    }
                }) { Text("記録を再開") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.finishUnfinished(onRecordingFinished) }) { Text("終了して保存") }
            },
        )
    }
}

/** 手袋でも押しやすい大きさ（48dp 以上）の地図ボタン（NFR-USA-02）。 */
@Composable
private fun MapButton(icon: ImageVector, description: String, onClick: () -> Unit, highlighted: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp,
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
private fun BigButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tonal: Boolean = false,
    danger: Boolean = false,
) {
    val modifier = Modifier.heightIn(min = 56.dp)
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
    when {
        tonal -> FilledTonalButton(onClick = onClick, modifier = modifier) { content() }
        danger -> Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { content() }
        else -> Button(onClick = onClick, modifier = modifier) { content() }
    }
}
