package com.mobilemotodroid.fuji.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** 画面表示用の書式（FR-SET-02：距離は km / m、標高は m）。 */
object Format {
    fun distance(meters: Double): String =
        if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale.JAPAN, "%.2f km", meters / 1000)

    /** 例：1:05:09 */
    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return String.format(Locale.JAPAN, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** 例：3時間12分 */
    fun durationShort(seconds: Long): String {
        val minutes = (seconds.coerceAtLeast(0) / 60.0).roundToLong()
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}時間${m}分" else "${m}分"
    }

    fun altitude(meters: Double?): String =
        meters?.let { String.format(Locale.JAPAN, "%,d m", it.roundToInt()) } ?: "-- m"

    fun gain(meters: Double): String = String.format(Locale.JAPAN, "%,d m", meters.roundToInt())

    fun bytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.JAPAN, "%.1f GB", bytes / 1024.0 / 1024 / 1024)
        bytes >= 1024L * 1024 -> String.format(Locale.JAPAN, "%.1f MB", bytes / 1024.0 / 1024)
        else -> String.format(Locale.JAPAN, "%d KB", (bytes / 1024).coerceAtLeast(if (bytes > 0) 1L else 0L))
    }

    fun dateTime(epochMs: Long): String = SimpleDateFormat("yyyy/MM/dd(E) HH:mm", Locale.JAPAN).format(Date(epochMs))
    fun date(epochMs: Long): String = SimpleDateFormat("yyyy/MM/dd(E)", Locale.JAPAN).format(Date(epochMs))

    /** FR-MAP-07 / FR-SAF-02：緯度経度（10 進数 6 桁 ≒ 10 cm）。 */
    fun coordinates(latitude: Double, longitude: Double): String =
        String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude)
}
