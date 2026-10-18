package com.mobilemotodroid.fuji.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** 共有・外部アプリの起動など。 */
object Intents {

    /** FR-SAF-01：現在地の座標と地図リンクを共有する。 */
    fun shareLocation(context: Context, latitude: Double, longitude: Double) {
        val text = buildString {
            appendLine("現在地：${Format.coordinates(latitude, longitude)}")
            appendLine("地理院地図：${gsiMapUrl(latitude, longitude)}")
            append("Google マップ：https://www.google.com/maps/search/?api=1&query=${coord(latitude)},${coord(longitude)}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(context, Intent.createChooser(intent, "現在地を共有"))
    }

    /** FR-SAF-02 */
    fun copyCoordinates(context: Context, latitude: Double, longitude: Double) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("現在地", Format.coordinates(latitude, longitude)))
        // Android 13 以降はシステムがコピーを表示する
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "座標をコピーしました", Toast.LENGTH_SHORT).show()
        }
    }

    /** FR-ACT-09：GPX ファイルを他のアプリに渡す。 */
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        start(context, Intent.createChooser(intent, title))
    }

    fun openUrl(context: Context, url: String) = start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /** FR-REC-10：バッテリー最適化の設定画面を開く。 */
    fun openBatteryOptimizationSettings(context: Context) {
        val opened = start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), showError = false)
        if (!opened) openAppSettings(context)
    }

    fun openAppSettings(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }

    fun gsiMapUrl(latitude: Double, longitude: Double, zoom: Int = 16) =
        "https://maps.gsi.go.jp/#$zoom/${coord(latitude)}/${coord(longitude)}/"

    private fun coord(v: Double) = String.format(Locale.ROOT, "%.6f", v)

    private fun start(context: Context, intent: Intent, showError: Boolean = true): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        if (showError) Toast.makeText(context, "対応するアプリが見つかりません", Toast.LENGTH_SHORT).show()
        false
    }
}

/** FR-REC-11：メーカー独自の省電力機能についての案内。 */
object BatteryGuide {
    const val DONT_KILL_MY_APP_URL = "https://dontkillmyapp.com/"

    fun manufacturerAdvice(): String {
        val m = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return when {
            "xiaomi" in m || "redmi" in m || "poco" in m ->
                "Xiaomi の端末：設定 →「アプリ」→ このアプリ →「バッテリーセーバー」を「制限なし」にし、「自動起動」をオンにしてください。"
            "huawei" in m || "honor" in m ->
                "Huawei / Honor の端末：設定 →「バッテリー」→「アプリ起動」で、このアプリを「手動で管理」にし、すべてオンにしてください。"
            "oppo" in m || "realme" in m || "oneplus" in m ->
                "OPPO / OnePlus の端末：設定 →「バッテリー」→ このアプリの「バックグラウンドでの動作を許可」をオンにしてください。"
            "samsung" in m ->
                "Galaxy の端末：設定 →「バッテリー」→「バックグラウンドでの使用制限」で、このアプリを「スリープしないアプリ」に追加してください。"
            "sony" in m ->
                "Xperia の端末：設定 →「バッテリー」→「STAMINA モード」をオフにするか、このアプリを例外に追加してください。"
            "sharp" in m ->
                "AQUOS の端末：設定 →「アプリ」→ このアプリ →「バッテリー」を「制限なし」にしてください。"
            else ->
                "端末によっては、独自の省電力機能で記録が止まることがあります。設定でこのアプリの電池の使用を「制限なし」にしてください。"
        }
    }

    fun dontKillMyAppUrl(): String =
        DONT_KILL_MY_APP_URL + Build.MANUFACTURER.lowercase(Locale.ROOT).replace(" ", "-")
}
