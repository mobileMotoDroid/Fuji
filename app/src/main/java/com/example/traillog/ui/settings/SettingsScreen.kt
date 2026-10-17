package com.example.traillog.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.traillog.BuildConfig
import com.example.traillog.data.settings.AppSettings
import com.example.traillog.data.settings.MapType
import com.example.traillog.data.settings.RecordingInterval
import com.example.traillog.data.settings.SettingsRepository
import com.example.traillog.ui.common.BatteryGuide
import com.example.traillog.ui.common.Intents
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setInterval(v: RecordingInterval) = viewModelScope.launch { repository.setRecordingInterval(v) }
    fun setDeviationEnabled(v: Boolean) = viewModelScope.launch { repository.setDeviationAlertEnabled(v) }
    fun setDeviationThreshold(v: Int) = viewModelScope.launch { repository.setDeviationThresholdM(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { repository.setKeepScreenOn(v) }
    fun setMapType(v: MapType) = viewModelScope.launch { repository.setMapType(v) }
}

private enum class TextDialog(val title: String, val body: String) {
    TERMS("利用規約", LegalTexts.TERMS),
    PRIVACY("プライバシーポリシー", LegalTexts.PRIVACY),
    DISCLAIMER("免責事項", LegalTexts.DISCLAIMER),
}

/** SCR-10：設定（F8）。 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<TextDialog?>(null) }
    var showLicenses by remember { mutableStateOf(false) }
    // スライダーを動かしている間は保存せず、離したときに保存する
    var thresholdDraft by remember { mutableStateOf<Float?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("設定") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            Section("記録")
            Text(
                "記録間隔",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            RecordingInterval.entries.forEach { interval ->
                RadioRow(
                    label = interval.label,
                    selected = settings.recordingInterval == interval,
                    onClick = { viewModel.setInterval(interval) },
                )
            }
            SwitchRow(
                label = "記録中は画面を消さない",
                description = "電池の消費が増えます",
                checked = settings.keepScreenOn,
                onChange = viewModel::setKeepScreenOn,
            )
            TextRow("バッテリーの最適化を設定する", BatteryGuide.manufacturerAdvice()) {
                Intents.openBatteryOptimizationSettings(context)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("ルート逸脱の警告")
            SwitchRow(
                label = "ルートから外れたら警告する",
                description = "通知と振動で知らせます",
                checked = settings.deviationAlertEnabled,
                onChange = viewModel::setDeviationEnabled,
            )
            val threshold = thresholdDraft ?: settings.deviationThresholdM.toFloat()
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("警告する距離：${threshold.roundToInt()} m 以上")
                Slider(
                    value = threshold,
                    onValueChange = { thresholdDraft = (it / 10).roundToInt() * 10f },
                    onValueChangeFinished = {
                        thresholdDraft?.let { viewModel.setDeviationThreshold(it.roundToInt()) }
                        thresholdDraft = null
                    },
                    valueRange = 20f..200f,
                    steps = 17,
                    enabled = settings.deviationAlertEnabled,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("地図")
            MapType.entries.forEach { type ->
                RadioRow(type.label, settings.mapType == type) { viewModel.setMapType(type) }
            }
            Text(
                "単位：距離は m / km、標高は m で表示します。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("データ")
            Text(
                "記録・写真・ルートはこの端末の中にだけ保存されます。アプリをアンインストールすると、すべて削除されます。" +
                    "大切な記録は、活動の詳細画面から GPX で書き出して保管してください。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section("このアプリについて")
            TextRow("利用規約") { dialog = TextDialog.TERMS }
            TextRow("プライバシーポリシー") { dialog = TextDialog.PRIVACY }
            TextRow("免責事項") { dialog = TextDialog.DISCLAIMER }
            TextRow("ライセンス") { showLicenses = true }
            TextRow("バージョン", "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）") {}
        }
    }

    dialog?.let { d ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(d.title) },
            text = { Text(d.body, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("閉じる") } },
        )
    }
    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("ライセンス") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LegalTexts.LICENSES.forEach { (name, license) ->
                        Column {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(license, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("閉じる") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SwitchRow(label: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun TextRow(label: String, description: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
