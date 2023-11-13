package cc.chenhe.qqnotifyevo.ui.advanced

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import cc.chenhe.qqnotifyevo.MyApplication
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.core.AvatarManager
import cc.chenhe.qqnotifyevo.ui.common.MviAndroidViewModel
import cc.chenhe.qqnotifyevo.utils.ACTION_DELETE_NEVO_CHANNEL
import cc.chenhe.qqnotifyevo.utils.AvatarCacheAge
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_AVATAR_CACHE_AGE
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_AVATAR_CACHE_AGE_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_ENABLE_LOG
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_ENABLE_LOG_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_FORMAT_NICKNAME
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_FORMAT_NICKNAME_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_NICKNAME_FORMAT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_NICKNAME_FORMAT_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SHOW_IN_RECENT_APPS
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SHOW_IN_RECENT_APPS_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SHOW_SPECIAL_PREFIX
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SHOW_SPECIAL_PREFIX_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SPECIAL_GROUP_CHANNEL
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SPECIAL_GROUP_CHANNEL_DEFAULT
import cc.chenhe.qqnotifyevo.utils.SpecialGroupChannel
import cc.chenhe.qqnotifyevo.utils.USAGE_TIP_NEVO_MULTI_MESSAGE
import cc.chenhe.qqnotifyevo.utils.USAGE_TIP_NEVO_MULTI_MESSAGE_DEFAULT
import cc.chenhe.qqnotifyevo.utils.dataStore
import cc.chenhe.qqnotifyevo.utils.describeFileSize
import cc.chenhe.qqnotifyevo.utils.getAvatarCachePeriod
import cc.chenhe.qqnotifyevo.utils.getAvatarDiskCacheDir
import cc.chenhe.qqnotifyevo.utils.getLogDir
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

data class AdvancedOptionsUiState(
    val specialPrefix: Boolean = PREFERENCE_SHOW_SPECIAL_PREFIX_DEFAULT,
    val specialInGroupChannel: SpecialGroupChannel = PREFERENCE_SPECIAL_GROUP_CHANNEL_DEFAULT,
    val formatNickname: Boolean = PREFERENCE_FORMAT_NICKNAME_DEFAULT,
    val nicknameFormat: String = PREFERENCE_NICKNAME_FORMAT_DEFAULT,
    val avatarCacheAge: AvatarCacheAge = PREFERENCE_AVATAR_CACHE_AGE_DEFAULT,
    val deleteAvatarCacheDone: Boolean = false,
    val deleteNevoChannelDone: Boolean = false,
    val resetUsageTipDone: Boolean = false,
    val showInRecentApps: Boolean = PREFERENCE_SHOW_IN_RECENT_APPS_DEFAULT,
    val enableLog: Boolean = PREFERENCE_ENABLE_LOG_DEFAULT,
    val logSize: String = "",
)

sealed interface AdvancedOptionsIntent {
    data class SetSpecialPrefix(val showPrefix: Boolean) : AdvancedOptionsIntent
    data class SetSpecialGroupChannel(val v: SpecialGroupChannel) : AdvancedOptionsIntent
    data class SetFormatNickname(val v: Boolean) : AdvancedOptionsIntent
    data class SetNicknameFormat(val format: String) : AdvancedOptionsIntent
    data class SetAvatarCacheAge(val avatarCacheAge: AvatarCacheAge) : AdvancedOptionsIntent
    data object DeleteAvatarCache : AdvancedOptionsIntent
    data object DeleteNevoChannel : AdvancedOptionsIntent
    data object ResetUsageTips : AdvancedOptionsIntent
    data class SetShowInRecentApps(val v: Boolean) : AdvancedOptionsIntent
    data class SetEnableLog(val v: Boolean) : AdvancedOptionsIntent
    data object DeleteLog : AdvancedOptionsIntent
}

class AdvancedOptionsViewModel(application: Application) :
    MviAndroidViewModel<AdvancedOptionsUiState, AdvancedOptionsIntent>(
        application,
        AdvancedOptionsUiState()
    ) {
    companion object {
        private const val TOAST_DURATION = 2000L
    }

    init {
        viewModelScope.launch {
            application.dataStore.data.collectLatest { prefs ->
                updateUiStateFromPreferences(prefs)
            }
        }
        viewModelScope.launch {
            _uiState.getAndUpdate { it.copy(logSize = calculateLogSize()) }
        }
    }

    private fun updateUiStateFromPreferences(prefs: Preferences) {
        _uiState.getAndUpdate {
            it.copy(
                specialPrefix = prefs[PREFERENCE_SHOW_SPECIAL_PREFIX]
                    ?: PREFERENCE_SHOW_SPECIAL_PREFIX_DEFAULT,
                specialInGroupChannel = SpecialGroupChannel.fromValue(prefs[PREFERENCE_SPECIAL_GROUP_CHANNEL]),
                formatNickname = prefs[PREFERENCE_FORMAT_NICKNAME]
                    ?: PREFERENCE_FORMAT_NICKNAME_DEFAULT,
                nicknameFormat = prefs[PREFERENCE_NICKNAME_FORMAT]
                    ?: PREFERENCE_NICKNAME_FORMAT_DEFAULT,
                avatarCacheAge = AvatarCacheAge.fromValue(prefs[PREFERENCE_AVATAR_CACHE_AGE]),
                showInRecentApps = prefs[PREFERENCE_SHOW_IN_RECENT_APPS]
                    ?: PREFERENCE_SHOW_IN_RECENT_APPS_DEFAULT,
                enableLog = prefs[PREFERENCE_ENABLE_LOG] ?: PREFERENCE_ENABLE_LOG_DEFAULT,
            )
        }
    }

    override suspend fun handleViewIntent(intent: AdvancedOptionsIntent) {
        val ctx: Context = getApplication()
        when (intent) {
            is AdvancedOptionsIntent.SetSpecialPrefix ->
                setBoolPreference(PREFERENCE_SHOW_SPECIAL_PREFIX, intent.showPrefix)

            is AdvancedOptionsIntent.SetSpecialGroupChannel -> {
                getApplication<Application>().dataStore.edit {
                    it[PREFERENCE_SPECIAL_GROUP_CHANNEL] = intent.v.v
                }
            }

            is AdvancedOptionsIntent.SetFormatNickname ->
                setBoolPreference(PREFERENCE_FORMAT_NICKNAME, intent.v)

            is AdvancedOptionsIntent.SetNicknameFormat -> {
                getApplication<Application>().dataStore.edit {
                    it[PREFERENCE_NICKNAME_FORMAT] = intent.format
                }
            }

            is AdvancedOptionsIntent.SetAvatarCacheAge -> {
                getApplication<Application>().dataStore.edit {
                    it[PREFERENCE_AVATAR_CACHE_AGE] = intent.avatarCacheAge.v
                }
            }

            AdvancedOptionsIntent.DeleteAvatarCache -> {
                AvatarManager
                    .get(getAvatarDiskCacheDir(ctx), getAvatarCachePeriod(ctx))
                    .clearCache()
                _uiState.getAndUpdate { it.copy(deleteAvatarCacheDone = true) }
                viewModelScope.launch {
                    delay(TOAST_DURATION)
                    _uiState.getAndUpdate { it.copy(deleteAvatarCacheDone = false) }
                }
            }

            AdvancedOptionsIntent.DeleteNevoChannel -> {
                ctx.sendBroadcast(Intent(ACTION_DELETE_NEVO_CHANNEL))
                _uiState.getAndUpdate { it.copy(deleteNevoChannelDone = true) }
                viewModelScope.launch {
                    delay(TOAST_DURATION)
                    _uiState.getAndUpdate { it.copy(deleteNevoChannelDone = false) }
                }
            }

            AdvancedOptionsIntent.ResetUsageTips -> {
                setBoolPreference(
                    USAGE_TIP_NEVO_MULTI_MESSAGE,
                    USAGE_TIP_NEVO_MULTI_MESSAGE_DEFAULT
                )
                _uiState.getAndUpdate { it.copy(resetUsageTipDone = true) }
                viewModelScope.launch {
                    delay(TOAST_DURATION)
                    _uiState.getAndUpdate { it.copy(resetUsageTipDone = false) }
                }
            }

            is AdvancedOptionsIntent.SetShowInRecentApps ->
                setBoolPreference(PREFERENCE_SHOW_IN_RECENT_APPS, intent.v)

            is AdvancedOptionsIntent.SetEnableLog ->
                setBoolPreference(PREFERENCE_ENABLE_LOG, intent.v)

            AdvancedOptionsIntent.DeleteLog -> {
                getApplication<MyApplication>().deleteLog()
                _uiState.getAndUpdate { it.copy(logSize = calculateLogSize()) }
            }
        }
    }

    private suspend fun setBoolPreference(key: Preferences.Key<Boolean>, v: Boolean) {
        getApplication<Application>().dataStore.edit {
            it[key] = v
        }
    }

    private fun calculateLogSize(): String {
        val files = getLogDir(getApplication()).listFiles { f -> f.isFile }
        val size = files?.sumOf { f -> f.length() } ?: 0
        return getApplication<Application>().getString(
            R.string.pref_delete_log_summary,
            files?.size ?: 0,
            describeFileSize(size)
        )
    }
}