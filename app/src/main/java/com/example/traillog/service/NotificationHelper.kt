package com.example.traillog.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.traillog.MainActivity
import com.example.traillog.R
import com.example.traillog.ui.common.Format
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 通知チャンネルの作成と、各種通知の発行。 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDING, "記録中", NotificationManager.IMPORTANCE_LOW).apply {
                description = "GPS の記録中に表示されます"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "警告", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "ルートからの逸脱、電池残量の低下などの警告"
                enableVibration(true)
                vibrationPattern = ALERT_PATTERN
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DOWNLOADS, "地図のダウンロード", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    /** FR-REC-02, 03：記録中の常駐通知。一時停止・再開のボタンを付ける。 */
    fun recordingNotification(isPaused: Boolean, distanceM: Double, movingTimeSec: Long): Notification {
        val title = if (isPaused) "一時停止中" else "記録中"
        val text = "距離 ${Format.distance(distanceM)} ・ 経過 ${Format.duration(movingTimeSec)}"
        val action = if (isPaused) {
            NotificationCompat.Action(0, "再開", servicePendingIntent(RecordingService.ACTION_RESUME, 1))
        } else {
            NotificationCompat.Action(0, "一時停止", servicePendingIntent(RecordingService.ACTION_PAUSE, 2))
        }
        return NotificationCompat.Builder(context, CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_mountain)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(action)
            .build()
    }

    /** FR-RTE-04：ルート逸脱の警告。通知の許可がなくても振動はする。 */
    fun notifyDeviation(distanceM: Double) {
        vibrate()
        notifyIfAllowed(
            ID_DEVIATION,
            alert("ルートから外れています", "予定ルートから約 ${distanceM.toInt()} m 離れています。現在地を確認してください。")
                .setTimeoutAfter(10 * 60_000L)
                .build(),
        )
    }

    /** FR-RTE-05：ルートへの復帰。 */
    fun notifyReturnedToRoute() {
        notifyIfAllowed(
            ID_DEVIATION,
            NotificationCompat.Builder(context, CHANNEL_RECORDING)
                .setSmallIcon(R.drawable.ic_stat_mountain)
                .setContentTitle("ルートに戻りました")
                .setContentIntent(openAppPendingIntent())
                .setAutoCancel(true)
                .setTimeoutAfter(60_000L)
                .build(),
        )
    }

    /** FR-SAF-05 */
    fun notifyLowBattery(percent: Int) {
        notifyIfAllowed(
            ID_BATTERY,
            alert("電池残量が少なくなっています（$percent%）", "記録間隔を省電力にする、画面を消すなどして電池を節約してください。").build(),
        )
    }

    /** NFR-REL-03 */
    fun notifyLowStorage() {
        notifyIfAllowed(
            ID_STORAGE,
            alert("空き容量が少なくなっています", "記録は続けていますが、容量がなくなると保存できなくなります。不要なファイルを削除してください。").build(),
        )
    }

    fun downloadNotification(title: String, progress: Float?): Notification =
        NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_stat_mountain)
            .setContentTitle(title)
            .setContentText(progress?.let { "${(it * 100).toInt()}%" } ?: "準備中…")
            .setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent())
            .build()

    @SuppressLint("MissingPermission")
    fun notifyIfAllowed(id: Int, notification: Notification) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) manager.notify(id, notification)
    }

    fun cancel(id: Int) = manager.cancel(id)

    private fun alert(title: String, text: String) = NotificationCompat.Builder(context, CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_stat_mountain)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setVibrate(ALERT_PATTERN)
        .setContentIntent(openAppPendingIntent())
        .setAutoCancel(true)

    /** 画面オフでも気づけるよう、通知とは別に端末を振動させる。 */
    fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createWaveform(ALERT_PATTERN, -1))
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context,
        requestCode,
        Intent(context, RecordingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_DOWNLOADS = "downloads"

        const val ID_RECORDING = 1001
        const val ID_DEVIATION = 1002
        const val ID_BATTERY = 1003
        const val ID_STORAGE = 1004
        const val ID_DOWNLOAD_BASE = 2000

        private val ALERT_PATTERN = longArrayOf(0, 600, 250, 600, 250, 900)
    }
}
