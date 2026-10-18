package com.mobilemotodroid.fuji.data.location

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 地図画面を表示している間だけ現在地を取得する（FR-MAP-03）。
 * 画面が見えなくなると収集が止まり、位置の取得も止まる（NFR-BAT-03）。
 * 記録そのものは RecordingService が行う。
 */
@Singleton
class ForegroundLocationSource @Inject constructor(
    private val fusedClient: FusedLocationProviderClient,
) {
    /** 位置情報の権限があることを確認してから収集すること。 */
    @SuppressLint("MissingPermission")
    fun locations(intervalMs: Long = 2_000L): Flow<Location> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        fusedClient.lastLocation.addOnSuccessListener { last -> last?.let { trySend(it) } }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { Log.w("ForegroundLocation", "requestLocationUpdates failed", it) }
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
