package com.sermonstudio.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sermonstudio.app.data.prefs.AppSettings
import com.sermonstudio.app.data.prefs.LocaleHelper
import com.sermonstudio.app.data.prefs.SettingsRepository
import com.sermonstudio.app.ui.AppNav
import com.sermonstudio.app.ui.theme.SermonStudioTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    // Applies the in-app language (English / Tagalog / Cebuano) chosen in Settings.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s by vm.settings.collectAsStateWithLifecycle()
            SermonStudioTheme(mode = s.themeMode, dynamicColor = s.dynamicColor) {
                AppNav()
            }
        }
    }
}
