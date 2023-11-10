package cc.chenhe.qqnotifyevo.ui

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import cc.chenhe.qqnotifyevo.ui.common.MviAndroidViewModel
import cc.chenhe.qqnotifyevo.utils.Mode
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_MODE
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SHOW_IN_RECENT_APPS
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SHOW_IN_RECENT_APPS_DEFAULT
import cc.chenhe.qqnotifyevo.utils.dataStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class MainUiState(
    val showMultiMessageWarning: Boolean = false,
)

sealed interface MainViewIntent {
    data class ShowMultiMessageWarning(val show: Boolean) : MainViewIntent
    data object ChangeToLegacyMode : MainViewIntent
}

class MainViewModel(application: Application) :
    MviAndroidViewModel<MainUiState, MainViewIntent>(application, MainUiState()) {

    var showInRecent: Boolean = PREFERENCE_SHOW_IN_RECENT_APPS_DEFAULT
        private set

    init {
        viewModelScope.launch {
            application.dataStore.data.map { it[PREFERENCE_SHOW_IN_RECENT_APPS] }.collectLatest {
                showInRecent = it ?: PREFERENCE_SHOW_IN_RECENT_APPS_DEFAULT
            }
        }
    }

    override suspend fun handleViewIntent(intent: MainViewIntent) {
        when (intent) {
            is MainViewIntent.ShowMultiMessageWarning -> {
                _uiState.getAndUpdate { it.copy(showMultiMessageWarning = intent.show) }
            }

            MainViewIntent.ChangeToLegacyMode ->
                getApplication<Application>().dataStore.edit {
                    it[PREFERENCE_MODE] = Mode.Legacy.v
                }
        }
    }

}