package com.mobilemotodroid.fuji.ui.activity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mobilemotodroid.fuji.data.db.ActivityEntity
import com.mobilemotodroid.fuji.ui.common.Format
import com.mobilemotodroid.fuji.ui.theme.StatValueStyle

/** FR-ACT-03：活動の統計。 */
@Composable
fun StatsGrid(activity: ActivityEntity, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row {
                StatCell("行動時間", Format.duration(activity.movingTimeSec))
                StatCell("距離", Format.distance(activity.distanceM))
            }
            Row {
                StatCell("累積の登り", "↑ ${Format.gain(activity.ascentM)}")
                StatCell("累積の下り", "↓ ${Format.gain(activity.descentM)}")
            }
            Row {
                StatCell("最高標高", Format.altitude(activity.maxAltitudeM))
                StatCell("最低標高", Format.altitude(activity.minAltitudeM))
            }
        }
    }
}

@Composable
private fun RowScope.StatCell(label: String, value: String) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = StatValueStyle)
    }
}

/** 標高グラフの点。x は距離（m）または経過時間（秒）。 */
data class ProfilePoint(val x: Float, val altitude: Float)

data class ElevationProfile(val byDistance: List<ProfilePoint>, val byTime: List<ProfilePoint>) {
    val isEmpty: Boolean get() = byDistance.size < 2
}

/** FR-ACT-04：標高グラフ（横軸は距離と時間を切り替えられる）。 */
@Composable
fun ElevationChart(profile: ElevationProfile, modifier: Modifier = Modifier) {
    var byTime by rememberSaveable { mutableStateOf(false) }
    val points = if (byTime) profile.byTime else profile.byDistance
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("標高グラフ", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = !byTime,
                        onClick = { byTime = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("距離") }
                    SegmentedButton(
                        selected = byTime,
                        onClick = { byTime = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("時間") }
                }
            }
            if (points.size < 2) {
                Text("標高のデータがありません", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            val minAlt = points.minOf { it.altitude }
            val maxAlt = points.maxOf { it.altitude }
            val maxX = points.last().x.coerceAtLeast(1f)
            Row {
                Column(Modifier.height(160.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(Format.altitude(maxAlt.toDouble()), style = MaterialTheme.typography.labelSmall)
                    Text(Format.altitude(minAlt.toDouble()), style = MaterialTheme.typography.labelSmall)
                }
                Canvas(
                    Modifier
                        .weight(1f)
                        .height(160.dp)
                        .padding(start = 8.dp),
                ) {
                    val range = (maxAlt - minAlt).coerceAtLeast(10f)
                    fun px(p: ProfilePoint) = Offset(
                        x = p.x / maxX * size.width,
                        y = size.height - (p.altitude - minAlt) / range * size.height,
                    )
                    for (i in 0..4) {
                        val y = size.height * i / 4f
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    val line = Path().apply {
                        val first = px(points.first())
                        moveTo(first.x, first.y)
                        points.drop(1).forEach { val o = px(it); lineTo(o.x, o.y) }
                    }
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(fill, fillColor)
                    drawPath(line, lineColor, style = Stroke(width = 3.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = MaterialTheme.typography.labelSmall)
                Text(
                    if (byTime) Format.durationShort(maxX.toLong()) else Format.distance(maxX.toDouble()),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
