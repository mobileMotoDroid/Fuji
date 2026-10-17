package com.example.traillog.ui.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.traillog.data.map.MapStyles

private val ScaleSteps = listOf(
    5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0,
    1_000.0, 2_000.0, 5_000.0, 10_000.0, 20_000.0, 50_000.0, 100_000.0, 200_000.0, 500_000.0,
)

/** FR-MAP-06：縮尺バー。[metersPerDp] は TileMath.metersPerDp で求める。 */
@Composable
fun ScaleBar(metersPerDp: Double?, modifier: Modifier = Modifier, maxWidthDp: Float = 110f) {
    if (metersPerDp == null || metersPerDp <= 0.0) return
    val meters = ScaleSteps.lastOrNull { it / metersPerDp <= maxWidthDp } ?: ScaleSteps.first()
    val widthDp = (meters / metersPerDp).toFloat().coerceAtMost(maxWidthDp)
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (meters >= 1000) "${(meters / 1000).toInt()} km" else "${meters.toInt()} m",
            color = Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            Modifier
                .width(widthDp.dp)
                .height(4.dp)
                .background(Color.Black),
        )
    }
}

/** FR-MAP-08：地図データの出典。 */
@Composable
fun MapAttribution(modifier: Modifier = Modifier) {
    Text(
        text = MapStyles.ATTRIBUTION,
        color = Color.Black,
        fontSize = 11.sp,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 小さな情報ラベル（座標など）。 */
@Composable
fun MapLabel(text: String, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (emphasis) Color.White else Color.Black,
        modifier = modifier
            .background(
                if (emphasis) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.85f),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 通信できる状態かどうか（FR-OFF-10 の案内表示に使う）。 */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    var online by remember { mutableStateOf(isOnlineNow(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = isOnlineNow(context) }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        runCatching { cm?.registerNetworkCallback(request, callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return online
}

private fun isOnlineNow(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
