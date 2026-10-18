package com.mobilemotodroid.fuji.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobilemotodroid.fuji.data.db.ActivityEntity
import com.mobilemotodroid.fuji.data.repository.ActivityRepository
import com.mobilemotodroid.fuji.ui.common.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ActivityListViewModel @Inject constructor(repository: ActivityRepository) : ViewModel() {
    val activities: StateFlow<List<ActivityEntity>?> =
        repository.observeFinished().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** SCR-05：活動一覧（FR-ACT-01）。 */
@Composable
fun ActivityListScreen(onOpen: (Long) -> Unit, viewModel: ActivityListViewModel = hiltViewModel()) {
    val activities by viewModel.activities.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("活動記録") }) }) { padding ->
        val list = activities
        when {
            list == null -> Unit
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "まだ記録がありません。\n地図画面の「記録開始」から記録できます。",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.id }) { activity ->
                    Card(onClick = { onOpen(activity.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(Format.dateTime(activity.startedAt), style = MaterialTheme.typography.labelMedium)
                            Text(
                                activity.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("距離 ${Format.distance(activity.distanceM)}")
                                Text("時間 ${Format.durationShort(activity.movingTimeSec)}")
                                Text("↑ ${Format.gain(activity.ascentM)}")
                            }
                        }
                    }
                }
            }
        }
    }
}
