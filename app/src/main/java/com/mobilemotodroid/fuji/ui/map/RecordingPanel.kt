package com.mobilemotodroid.fuji.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mobilemotodroid.fuji.service.RecordingPhase
import com.mobilemotodroid.fuji.service.RecordingState
import com.mobilemotodroid.fuji.ui.common.Format
import com.mobilemotodroid.fuji.ui.theme.StatValueStyle

/** SCR-03：記録中の統計パネル（F4）。 */
@Composable
fun RecordingPanel(state: RecordingState, nowMs: Long, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (state.phase == RecordingPhase.PAUSED) {
                Text(
                    "一時停止中",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("経過時間", Format.duration(state.movingTimeMs(nowMs) / 1000))
                Stat("距離", Format.distance(state.distanceM))
                Stat("標高", Format.altitude(state.altitudeM))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Stat("登り", "↑ ${Format.gain(state.ascentM)}")
                Stat("下り", "↓ ${Format.gain(state.descentM)}")
                if (state.hasRoute && state.distanceToRouteM != null) {
                    Stat("ルートまで", Format.distance(state.distanceToRouteM))
                } else {
                    Stat("電池", state.batteryPercent?.let { "$it%" } ?: "--")
                }
            }
        }
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = StatValueStyle, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** FR-RTE-04：ルート逸脱中の表示。 */
@Composable
fun OffRouteBanner(distanceM: Double?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Text(
                text = "ルートから外れています" + (distanceM?.let { "（約 ${Format.distance(it)}）" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
