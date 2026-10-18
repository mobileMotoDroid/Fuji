package com.mobilemotodroid.fuji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mobilemotodroid.fuji.data.settings.SettingsRepository
import com.mobilemotodroid.fuji.ui.disclaimer.DisclaimerScreen
import com.mobilemotodroid.fuji.ui.navigation.AppNavHost
import com.mobilemotodroid.fuji.ui.theme.FujiTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FujiTheme {
                FujiRoot()
            }
        }
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    /** 読み込み中は null。 */
    val disclaimerAccepted: StateFlow<Boolean?> = settingsRepository.settings
        .map { it.disclaimerAccepted }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun acceptDisclaimer() = viewModelScope.launch { settingsRepository.setDisclaimerAccepted() }
}

@Composable
private fun FujiRoot(viewModel: MainViewModel = hiltViewModel()) {
    val accepted by viewModel.disclaimerAccepted.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (accepted) {
            null -> Box(Modifier.fillMaxSize())
            false -> DisclaimerScreen(onAccepted = { viewModel.acceptDisclaimer() }) // SCR-01 / FR-SAF-04
            true -> AppNavHost()
        }
    }
}
