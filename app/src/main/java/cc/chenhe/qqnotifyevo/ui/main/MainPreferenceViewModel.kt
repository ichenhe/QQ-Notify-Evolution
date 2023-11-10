package cc.chenhe.qqnotifyevo.ui.main

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import cc.chenhe.qqnotifyevo.service.NevoDecorator
import cc.chenhe.qqnotifyevo.service.NotificationMonitorService
import cc.chenhe.qqnotifyevo.ui.common.MviAndroidViewModel
import cc.chenhe.qqnotifyevo.utils.IconStyle
import cc.chenhe.qqnotifyevo.utils.Mode
import cc.chenhe.qqnotifyevo.utils.Mode.Legacy
import cc.chenhe.qqnotifyevo.utils.Mode.Nevo
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_ICON
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_MODE
import cc.chenhe.qqnotifyevo.utils.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

data class MainPreferenceUiState(
    val mode: Mode = Legacy,
    val iconStyle: IconStyle = IconStyle.Auto,
    val isServiceRunning: Boolean = true,
    val showNevoNotInstalledDialog: Boolean = false,
)

sealed interface MainPreferenceIntent {
    data class SetMode(val newMode: Mode) : MainPreferenceIntent
    data class SetIconStyle(val newIcon: IconStyle) : MainPreferenceIntent
    data class ShowNevoNotInstalledDialog(val show: Boolean) : MainPreferenceIntent
}

class MainPreferenceViewModel(application: Application) :
    MviAndroidViewModel<MainPreferenceUiState, MainPreferenceIntent>(
        application,
        MainPreferenceUiState()
    ) {
    companion object {
        private const val CHECK_SERVICE_INTERVAL = 1000L
    }

    init {
        viewModelScope.launch {
            application.dataStore.data.collectLatest { pref ->
                val newMode = Mode.fromValue(pref[PREFERENCE_MODE])
                _uiState.getAndUpdate {
                    it.copy(mode = newMode, iconStyle = IconStyle.fromValue(pref[PREFERENCE_ICON]))
                }
                when (newMode) {
                    Nevo -> while (true) {
                        updateServiceState(NevoDecorator.isRunning())
                        delay(CHECK_SERVICE_INTERVAL)
                    }

                    Legacy -> while (true) {
                        updateServiceState(NotificationMonitorService.isRunning())
                        delay(CHECK_SERVICE_INTERVAL)
                    }
                }
            }
        }
    }

    private fun updateServiceState(running: Boolean) {
        _uiState.getAndUpdate { old ->
            if (old.isServiceRunning == running) old else old.copy(isServiceRunning = running)
        }
    }

    override suspend fun handleViewIntent(intent: MainPreferenceIntent) {
        when (intent) {
            is MainPreferenceIntent.SetMode -> {
                getApplication<Application>().dataStore.edit { preferences ->
                    preferences[PREFERENCE_MODE] = intent.newMode.v
                }
            }

            is MainPreferenceIntent.SetIconStyle -> {
                getApplication<Application>().dataStore.edit { preferences ->
                    preferences[PREFERENCE_ICON] = intent.newIcon.v
                }
            }

            is MainPreferenceIntent.ShowNevoNotInstalledDialog -> {
                _uiState.getAndUpdate { it.copy(showNevoNotInstalledDialog = intent.show) }
            }
        }
    }

}