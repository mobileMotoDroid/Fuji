package com.mobilemotodroid.fuji.ui.save

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobilemotodroid.fuji.data.db.ActivityEntity
import com.mobilemotodroid.fuji.data.repository.ActivityRepository
import com.mobilemotodroid.fuji.ui.activity.StatsGrid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SaveRecordingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ActivityRepository,
) : ViewModel() {
    val activityId: Long = checkNotNull(savedStateHandle.get<Long>("activityId"))

    val activity: StateFlow<ActivityEntity?> =
        repository.observe(activityId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(title: String, memo: String, onDone: () -> Unit) = viewModelScope.launch {
        repository.updateTitleMemo(activityId, title, memo)
        onDone()
    }

    fun discard(onDone: () -> Unit) = viewModelScope.launch {
        repository.delete(activityId)
        onDone()
    }
}

/** SCR-04：記録の保存（FR-REC-12）。タイトルの初期値は日付。 */
@Composable
fun SaveRecordingScreen(
    onSaved: (Long) -> Unit,
    onDiscarded: () -> Unit,
    viewModel: SaveRecordingViewModel = hiltViewModel(),
) {
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    var title by rememberSaveable { mutableStateOf<String?>(null) }
    var memo by rememberSaveable { mutableStateOf("") }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(activity?.id) {
        val a = activity ?: return@LaunchedEffect
        if (title == null) {
            title = a.title
            memo = a.memo
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("記録の保存") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            activity?.let { StatsGrid(it) }
            OutlinedTextField(
                value = title.orEmpty(),
                onValueChange = { title = it },
                label = { Text("タイトル") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("メモ") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { confirmDiscard = true },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("破棄") }
                Button(
                    onClick = { viewModel.save(title.orEmpty(), memo) { onSaved(viewModel.activityId) } },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("保存") }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("記録を破棄しますか？") },
            text = { Text("軌跡と写真がすべて削除され、元に戻せません。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discard(onDiscarded)
                }) { Text("破棄する") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("キャンセル") } },
        )
    }
}
