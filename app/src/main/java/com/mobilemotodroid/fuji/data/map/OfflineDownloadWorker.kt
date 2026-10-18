package com.mobilemotodroid.fuji.data.map

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.mobilemotodroid.fuji.service.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 地図タイルのダウンロードを、アプリを閉じても続けるためのワーカー（FR-OFF-06）。
 * フォアグラウンドで動かし、進捗を通知に表示する。
 */
@HiltWorker
class OfflineDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: OfflineMapRepository,
    private val notifications: NotificationHelper,
) : CoroutineWorker(appContext, params) {

    private val regionId: Long get() = inputData.getLong(KEY_REGION_ID, -1L)
    private val notificationId: Int get() = NotificationHelper.ID_DOWNLOAD_BASE + (regionId % 1000).toInt()

    override suspend fun doWork(): Result {
        if (regionId < 0) return Result.failure()
        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            // バックグラウンドからはフォアグラウンド化できないことがある。その場合も通常のワーカーとして続ける。
            Log.w(TAG, "setForeground failed", e)
        }

        var lastNotifiedAt = 0L
        val completed = repository.downloadAndAwait(regionId) { info ->
            val now = System.currentTimeMillis()
            if (now - lastNotifiedAt > 1_000L || info.isComplete) {
                lastNotifiedAt = now
                notifications.notifyIfAllowed(notificationId, notifications.downloadNotification(info.name, info.progress))
            }
        }
        notifications.cancel(notificationId)
        return if (completed) Result.success() else Result.failure()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = notifications.downloadNotification("地図をダウンロード中", null)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    companion object {
        const val KEY_REGION_ID = "region_id"
        private const val TAG = "OfflineDownloadWorker"
    }
}
