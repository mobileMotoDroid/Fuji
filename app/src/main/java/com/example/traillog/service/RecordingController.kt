package com.example.traillog.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 画面から記録サービスを操作する。 */
@Singleton
class RecordingController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun start() = send(RecordingService.ACTION_START, foreground = true)

    /** FR-REC-05：残っている記録を再開する。 */
    fun restore() = send(RecordingService.ACTION_RESTORE, foreground = true)

    fun pause() = send(RecordingService.ACTION_PAUSE)
    fun resume() = send(RecordingService.ACTION_RESUME)
    fun stop() = send(RecordingService.ACTION_STOP)

    private fun send(action: String, foreground: Boolean = false) {
        val intent = Intent(context, RecordingService::class.java).setAction(action)
        if (foreground) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
    }
}
