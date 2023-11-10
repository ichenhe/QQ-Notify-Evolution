package cc.chenhe.qqnotifyevo.ui.common

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class MviAndroidViewModel<UiState, ViewIntent>(
    application: Application,
    initialUiState: UiState,
) :
    AndroidViewModel(application) {

    @Suppress("PropertyName")
    protected val _uiState = MutableStateFlow(initialUiState)
    val uiState = _uiState.asStateFlow()

    private val viewIntents = MutableSharedFlow<ViewIntent>()

    init {
        viewModelScope.launch {
            subscribeViewIntents()
        }
    }

    private suspend fun subscribeViewIntents() {
        viewIntents.collect { viewIntent ->
            handleViewIntent(viewIntent)
        }
    }

    abstract suspend fun handleViewIntent(intent: ViewIntent)

    fun sendIntent(viewIntent: ViewIntent) {
        viewModelScope.launch { viewIntents.emit(viewIntent) }
    }
}