package com.example.traillog.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** FR-REC-06 / FR-SET-01：記録間隔。 */
enum class RecordingInterval(val intervalMs: Long, val label: String) {
    HIGH_ACCURACY(3_000L, "高精度（約 3 秒）"),
    STANDARD(10_000L, "標準（約 10 秒）"),
    POWER_SAVER(30_000L, "省電力（約 30 秒）"),
}

/** FR-MAP-09：地図の種類。 */
enum class MapType(val label: String) {
    STANDARD("標準地図"),
    PHOTO("航空写真"),
}

/** FR-MAP-05：地図の向き。 */
enum class OrientationMode(val label: String) {
    NORTH_UP("北を上"),
    HEADING_UP("進行方向を上"),
}

data class AppSettings(
    val recordingInterval: RecordingInterval = RecordingInterval.STANDARD,
    val deviationAlertEnabled: Boolean = true,
    val deviationThresholdM: Int = DEFAULT_DEVIATION_THRESHOLD_M,
    val keepScreenOn: Boolean = false,
    val mapType: MapType = MapType.STANDARD,
    val orientationMode: OrientationMode = OrientationMode.NORTH_UP,
    val disclaimerAccepted: Boolean = false,
    val batteryGuideShown: Boolean = false,
    /** 地図に重ねて表示し、逸脱判定に使うルート。 */
    val selectedRouteId: Long? = null,
) {
    companion object {
        const val DEFAULT_DEVIATION_THRESHOLD_M = 50
        const val DEVIATION_DURATION_MS = 30_000L
        const val LOW_BATTERY_PERCENT = 20
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** 設定値を DataStore に保存する（要件定義書 7.2）。 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val INTERVAL = stringPreferencesKey("recording_interval")
        val DEVIATION_ENABLED = booleanPreferencesKey("deviation_enabled")
        val DEVIATION_THRESHOLD = intPreferencesKey("deviation_threshold_m")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val MAP_TYPE = stringPreferencesKey("map_type")
        val ORIENTATION = stringPreferencesKey("orientation_mode")
        val DISCLAIMER = booleanPreferencesKey("disclaimer_accepted")
        val BATTERY_GUIDE = booleanPreferencesKey("battery_guide_shown")
        val SELECTED_ROUTE = longPreferencesKey("selected_route_id")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                recordingInterval = enumOrDefault(p[Keys.INTERVAL], RecordingInterval.STANDARD),
                deviationAlertEnabled = p[Keys.DEVIATION_ENABLED] ?: true,
                deviationThresholdM = p[Keys.DEVIATION_THRESHOLD] ?: AppSettings.DEFAULT_DEVIATION_THRESHOLD_M,
                keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
                mapType = enumOrDefault(p[Keys.MAP_TYPE], MapType.STANDARD),
                orientationMode = enumOrDefault(p[Keys.ORIENTATION], OrientationMode.NORTH_UP),
                disclaimerAccepted = p[Keys.DISCLAIMER] ?: false,
                batteryGuideShown = p[Keys.BATTERY_GUIDE] ?: false,
                selectedRouteId = p[Keys.SELECTED_ROUTE],
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setRecordingInterval(value: RecordingInterval) = edit { it[Keys.INTERVAL] = value.name }
    suspend fun setDeviationAlertEnabled(value: Boolean) = edit { it[Keys.DEVIATION_ENABLED] = value }
    suspend fun setDeviationThresholdM(value: Int) = edit { it[Keys.DEVIATION_THRESHOLD] = value }
    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = value }
    suspend fun setMapType(value: MapType) = edit { it[Keys.MAP_TYPE] = value.name }
    suspend fun setOrientationMode(value: OrientationMode) = edit { it[Keys.ORIENTATION] = value.name }
    suspend fun setDisclaimerAccepted() = edit { it[Keys.DISCLAIMER] = true }
    suspend fun setBatteryGuideShown() = edit { it[Keys.BATTERY_GUIDE] = true }

    suspend fun setSelectedRouteId(value: Long?) = edit { prefs ->
        prefs.remove(Keys.SELECTED_ROUTE)
        if (value != null) prefs[Keys.SELECTED_ROUTE] = value
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { block(it) }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
