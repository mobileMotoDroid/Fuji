package com.mobilemotodroid.fuji.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.location.Location
import android.location.altitude.AltitudeConverter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mobilemotodroid.fuji.data.db.ActivityStatus
import com.mobilemotodroid.fuji.data.db.TrackPointEntity
import com.mobilemotodroid.fuji.data.repository.ActivityRepository
import com.mobilemotodroid.fuji.data.repository.RouteRepository
import com.mobilemotodroid.fuji.data.repository.toSample
import com.mobilemotodroid.fuji.data.settings.AppSettings
import com.mobilemotodroid.fuji.data.settings.SettingsRepository
import com.mobilemotodroid.fuji.domain.AltitudeFuser
import com.mobilemotodroid.fuji.domain.DeviationDetector
import com.mobilemotodroid.fuji.domain.GeoPoint
import com.mobilemotodroid.fuji.domain.GeoUtils
import com.mobilemotodroid.fuji.domain.LocationFilter
import com.mobilemotodroid.fuji.domain.LocationFix
import com.mobilemotodroid.fuji.domain.TrackAccumulator
import com.mobilemotodroid.fuji.domain.TrackSample
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * GPS 軌跡を記録する Foreground Service（F3）。
 *
 * - 位置は 1 点ごとに Room へ保存する（FR-REC-04）。強制終了されても直前までの点が残る（NFR-REL-02）。
 * - START_STICKY で再起動されたとき、または次回起動時に、記録中だった活動を復元する（FR-REC-05）。
 * - バックグラウンド位置権限は使わず、利用者の操作で開始した Foreground Service で記録する。
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var fusedClient: FusedLocationProviderClient
    @Inject lateinit var activityRepository: ActivityRepository
    @Inject lateinit var routeRepository: RouteRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var stateHolder: RecordingStateHolder
    @Inject lateinit var notifications: NotificationHelper
    @Inject lateinit var barometer: BarometerSource

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 操作（開始・一時停止など）と位置の保存を 1 つずつ順に処理するためのロック。 */
    private val mutex = Mutex()

    private var phase = RecordingPhase.IDLE
    private var activityId: Long? = null
    private var segment = 0
    private var startNewTrackSegment = true
    private var settings = AppSettings()
    private var requestedIntervalMs: Long? = null
    private var settingsJob: Job? = null

    private var filter = LocationFilter()
    private var accumulator = TrackAccumulator()
    private val altitudeFuser = AltitudeFuser()
    private val deviationDetector = DeviationDetector(durationMs = AppSettings.DEVIATION_DURATION_MS)
    private var routePoints: List<GeoPoint> = emptyList()

    private var batteryPercent: Int? = null
    private var lowBatteryNotified = false
    private var lowStorageNotified = false
    private var pointsSinceStorageCheck = 0
    private var lastNotificationAtMs = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val locations = result.locations.toList()
            scope.launch { mutex.withLock { locations.forEach { handleLocation(it) } } }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return
            val percent = level * 100 / scale
            val charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            batteryPercent = percent
            stateHolder.update { it.copy(batteryPercent = percent) }
            if (phase != RecordingPhase.IDLE && !charging && percent <= AppSettings.LOW_BATTERY_PERCENT && !lowBatteryNotified) {
                lowBatteryNotified = true
                notifications.notifyLowBattery(percent)
            }
            if (percent > AppSettings.LOW_BATTERY_PERCENT + 5) lowBatteryNotified = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // intent が null のときは、システムによる再起動（START_STICKY）
        val action = intent?.action ?: ACTION_RESTORE
        val needsForeground = action == ACTION_START || action == ACTION_RESTORE || phase == RecordingPhase.IDLE
        if (needsForeground && !startInForeground()) {
            // 権限がない、またはバックグラウンドからは開始できない。DB の状態は残るので、次回起動時に復元を案内する。
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            mutex.withLock {
                when (action) {
                    ACTION_START -> startNew()
                    ACTION_RESTORE -> restore()
                    ACTION_PAUSE -> { ensureLoaded(); pause() }
                    ACTION_RESUME -> { ensureLoaded(); resume() }
                    ACTION_STOP -> { ensureLoaded(); stop() }
                    else -> Unit
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        fusedClient.removeLocationUpdates(locationCallback)
        barometer.stop()
        runCatching { unregisterReceiver(batteryReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    // ---- 操作 ----

    private suspend fun startNew() {
        if (phase != RecordingPhase.IDLE) return
        // 前回の記録が残っていれば、新しく始めずにそれを復元する
        if (activityRepository.findUnfinished() != null) {
            restore()
            return
        }
        settings = settingsRepository.current()
        val now = System.currentTimeMillis()
        val id = activityRepository.create(now, settings.selectedRouteId)
        activityId = id
        segment = 0
        filter = LocationFilter()
        accumulator = TrackAccumulator()
        altitudeFuser.reset()
        deviationDetector.reset()
        loadRoute(settings.selectedRouteId)
        startNewTrackSegment = true
        phase = RecordingPhase.RECORDING

        stateHolder.reset()
        stateHolder.update {
            RecordingState(
                phase = RecordingPhase.RECORDING,
                activityId = id,
                resumedAtMs = now,
                batteryPercent = batteryPercent,
                hasRoute = routePoints.isNotEmpty(),
                hasBarometer = barometer.isAvailable,
            )
        }
        barometer.start()
        startLocationUpdates()
        observeSettings()
        updateNotification(force = true)
    }

    /** FR-REC-05：記録中・一時停止中のまま残っている活動を復元する。 */
    private suspend fun restore() {
        if (phase != RecordingPhase.IDLE) return
        val activity = activityRepository.findUnfinished()
        if (activity == null) {
            stopCompletely()
            return
        }
        settings = settingsRepository.current()
        activityId = activity.id
        loadRoute(activity.routeId)
        deviationDetector.reset()
        altitudeFuser.reset()
        filter = LocationFilter()

        val points = activityRepository.getPoints(activity.id)
        accumulator = TrackAccumulator().apply { addAll(points.map { it.toSample() }) }
        stateHolder.setTrack(
            points.groupBy { it.segment }.toSortedMap().values.map { seg -> seg.map { GeoPoint(it.latitude, it.longitude) } },
        )
        startNewTrackSegment = true

        val now = System.currentTimeMillis()
        if (activity.status == ActivityStatus.PAUSED) {
            phase = RecordingPhase.PAUSED
            segment = activity.currentSegment
        } else {
            // 中断していた間の移動は距離に含めないよう、新しい区間として再開する
            phase = RecordingPhase.RECORDING
            segment = activity.currentSegment + 1
            val resumedAt = activity.resumedAt ?: now
            activityRepository.update(activity.copy(currentSegment = segment, resumedAt = resumedAt))
        }
        stateHolder.update {
            it.copy(
                phase = phase,
                activityId = activity.id,
                movingTimeBaseMs = activity.movingTimeSec * 1000,
                resumedAtMs = if (phase == RecordingPhase.RECORDING) activity.resumedAt ?: now else null,
                distanceM = accumulator.distanceM,
                ascentM = accumulator.ascentM,
                descentM = accumulator.descentM,
                altitudeM = accumulator.lastSample?.altitudeM,
                batteryPercent = batteryPercent,
                hasRoute = routePoints.isNotEmpty(),
                hasBarometer = barometer.isAvailable,
            )
        }
        if (phase == RecordingPhase.RECORDING) {
            barometer.start()
            startLocationUpdates()
        }
        observeSettings()
        updateNotification(force = true)
    }

    private suspend fun ensureLoaded() {
        if (phase == RecordingPhase.IDLE) restore()
    }

    private suspend fun pause() {
        if (phase != RecordingPhase.RECORDING) return
        val id = activityId ?: return
        stopLocationUpdates()
        barometer.stop()
        val activity = activityRepository.get(id) ?: return
        val now = System.currentTimeMillis()
        val runningSec = activity.resumedAt?.let { ((now - it) / 1000).coerceAtLeast(0L) } ?: 0L
        val movingSec = activity.movingTimeSec + runningSec
        activityRepository.update(activity.copy(status = ActivityStatus.PAUSED, movingTimeSec = movingSec, resumedAt = null))
        phase = RecordingPhase.PAUSED
        deviationDetector.reset()
        stateHolder.update {
            it.copy(phase = RecordingPhase.PAUSED, movingTimeBaseMs = movingSec * 1000, resumedAtMs = null, isOffRoute = false)
        }
        updateNotification(force = true)
    }

    private suspend fun resume() {
        if (phase != RecordingPhase.PAUSED) return
        val id = activityId ?: return
        val activity = activityRepository.get(id) ?: return
        val now = System.currentTimeMillis()
        segment = activity.currentSegment + 1
        activityRepository.update(activity.copy(status = ActivityStatus.RECORDING, resumedAt = now, currentSegment = segment))
        filter.startNewSegment()
        startNewTrackSegment = true
        phase = RecordingPhase.RECORDING
        stateHolder.update { it.copy(phase = RecordingPhase.RECORDING, resumedAtMs = now) }
        barometer.start()
        startLocationUpdates()
        updateNotification(force = true)
    }

    private suspend fun stop() {
        val id = activityId
        stopLocationUpdates()
        barometer.stop()
        if (id != null) {
            activityRepository.finish(id, System.currentTimeMillis())
            notifications.cancel(NotificationHelper.ID_DEVIATION)
            stateHolder.markFinished(id)
        }
        phase = RecordingPhase.IDLE
        activityId = null
        stopCompletely()
    }

    private fun stopCompletely() {
        settingsJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- 位置の処理 ----

    private suspend fun handleLocation(location: Location) {
        if (phase != RecordingPhase.RECORDING) return
        val id = activityId ?: return

        stateHolder.update { it.copy(lastLocation = location) }
        val accuracy = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
        val decision = filter.evaluate(LocationFix(location.time, location.latitude, location.longitude, accuracy))
        if (decision != LocationFilter.Decision.ACCEPT) return

        val verticalAccuracy = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null
        val altitude = altitudeFuser.fuse(gpsAltitudeM(location), verticalAccuracy, barometer.altitudeM)

        val point = TrackPointEntity(
            activityId = id,
            time = location.time,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = altitude,
            accuracy = accuracy,
            speed = if (location.hasSpeed()) location.speed else null,
            pressureHpa = barometer.pressureHpa,
            segment = segment,
        )
        try {
            activityRepository.appendPoint(point)
        } catch (e: SQLiteFullException) {
            // NFR-REL-03：保存できなくても記録（画面表示）は止めずに警告する
            Log.e(TAG, "storage full", e)
            notifyLowStorageOnce()
        } catch (e: SQLiteException) {
            Log.e(TAG, "failed to save point", e)
        }

        accumulator.add(TrackSample(point.time, point.latitude, point.longitude, altitude, segment))
        stateHolder.appendPoint(GeoPoint(point.latitude, point.longitude), startNewTrackSegment)
        startNewTrackSegment = false

        var distanceToRoute: Double? = null
        if (routePoints.isNotEmpty()) {
            val d = GeoUtils.distanceToPolylineM(GeoPoint(point.latitude, point.longitude), routePoints)
            distanceToRoute = d
            if (settings.deviationAlertEnabled) {
                deviationDetector.thresholdM = settings.deviationThresholdM.toDouble()
                when (deviationDetector.update(d, point.time)) {
                    DeviationDetector.Event.DEVIATED -> notifications.notifyDeviation(d)
                    DeviationDetector.Event.RETURNED -> notifications.notifyReturnedToRoute()
                    null -> Unit
                }
            } else {
                deviationDetector.reset()
            }
        }

        stateHolder.update {
            it.copy(
                distanceM = accumulator.distanceM,
                ascentM = accumulator.ascentM,
                descentM = accumulator.descentM,
                altitudeM = altitude ?: it.altitudeM,
                distanceToRouteM = distanceToRoute,
                isOffRoute = deviationDetector.isDeviated,
            )
        }
        checkStorage()
        updateNotification(force = false)
    }

    /** 標高。Android 14 以降は楕円体高をジオイド高（海抜）に変換する。 */
    private suspend fun gpsAltitudeM(location: Location): Double? {
        if (!location.hasAltitude()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val msl = withContext(Dispatchers.IO) { MslAltitude.convert(this@RecordingService, location) }
            if (msl != null) return msl
        }
        return location.altitude
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "location permission missing")
            return
        }
        val interval = settings.recordingInterval.intervalMs
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(interval)
            .setWaitForAccurateLocation(false)
            .build()
        fusedClient.removeLocationUpdates(locationCallback)
        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            .addOnFailureListener { Log.e(TAG, "requestLocationUpdates failed", it) }
        requestedIntervalMs = interval
    }

    private fun stopLocationUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
        requestedIntervalMs = null
    }

    /** 記録中に設定（記録間隔・逸脱警告）が変わったら反映する。 */
    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settingsRepository.settings.collect { s ->
                val intervalChanged = s.recordingInterval.intervalMs != settings.recordingInterval.intervalMs
                settings = s
                if (phase == RecordingPhase.RECORDING && intervalChanged && requestedIntervalMs != null) {
                    startLocationUpdates()
                }
            }
        }
    }

    private suspend fun loadRoute(routeId: Long?) {
        routePoints = routeId?.let { routeRepository.getWithPoints(it)?.points }.orEmpty()
    }

    private fun checkStorage() {
        if (++pointsSinceStorageCheck < STORAGE_CHECK_EVERY) return
        pointsSinceStorageCheck = 0
        if (filesDir.usableSpace < LOW_STORAGE_BYTES) notifyLowStorageOnce() else lowStorageNotified = false
    }

    private fun notifyLowStorageOnce() {
        if (lowStorageNotified) return
        lowStorageNotified = true
        notifications.notifyLowStorage()
    }

    private fun startInForeground(): Boolean {
        if (!hasLocationPermission()) return false
        val notification = currentNotification()
        return try {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.ID_RECORDING,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            false
        }
    }

    private fun updateNotification(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationAtMs < NOTIFICATION_UPDATE_INTERVAL_MS) return
        lastNotificationAtMs = now
        notifications.notifyIfAllowed(NotificationHelper.ID_RECORDING, currentNotification())
    }

    private fun currentNotification() = stateHolder.state.value.let { s ->
        notifications.recordingNotification(
            isPaused = phase == RecordingPhase.PAUSED,
            distanceM = s.distanceM,
            movingTimeSec = s.movingTimeMs(System.currentTimeMillis()) / 1000,
        )
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START = "com.mobilemotodroid.fuji.action.START"
        const val ACTION_RESTORE = "com.mobilemotodroid.fuji.action.RESTORE"
        const val ACTION_PAUSE = "com.mobilemotodroid.fuji.action.PAUSE"
        const val ACTION_RESUME = "com.mobilemotodroid.fuji.action.RESUME"
        const val ACTION_STOP = "com.mobilemotodroid.fuji.action.STOP"

        private const val TAG = "RecordingService"
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 15_000L
        private const val STORAGE_CHECK_EVERY = 30
        private const val LOW_STORAGE_BYTES = 50L * 1024 * 1024
    }
}

/** Android 14 以降の AltitudeConverter で、海抜高度を求める。 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private object MslAltitude {
    private val converter by lazy { AltitudeConverter() }

    fun convert(context: Context, location: Location): Double? = try {
        converter.addMslAltitudeToLocation(context, location)
        if (location.hasMslAltitude()) location.mslAltitudeMeters else null
    } catch (e: Exception) {
        null
    }
}
