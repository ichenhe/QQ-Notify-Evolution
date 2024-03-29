package cc.chenhe.qqnotifyevo.ui.permission

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.concurrent.futures.await
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.lifecycle.viewModelScope
import cc.chenhe.qqnotifyevo.service.AccessibilityMonitorService
import cc.chenhe.qqnotifyevo.ui.common.MviAndroidViewModel
import cc.chenhe.qqnotifyevo.utils.Mode
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_MODE
import cc.chenhe.qqnotifyevo.utils.dataStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class PermissionUiState(
    val mode: Mode = Mode.Legacy,
    val ignoreBatteryOptimize: Boolean? = null,
    val unusedAppRestrictionsEnabled: Boolean? = null,
    val notificationAccess: Boolean? = null,
    val accessibility: Boolean? = null,
)

class PermissionViewModel(application: Application) :
    MviAndroidViewModel<PermissionUiState, Unit>(application, PermissionUiState()) {

    init {
        viewModelScope.launch {
            application.dataStore.data.map { it[PREFERENCE_MODE] }.collectLatest { newMode ->
                _uiState.getAndUpdate { it.copy(mode = Mode.fromValue(newMode)) }
            }
        }
    }

    override suspend fun handleViewIntent(intent: Unit) {
    }

    suspend fun refreshPermissionState() {
        refreshNotificationAccessState()
        refreshAccessibilityState()
        refreshIgnoreBatteryOptimizationState()
        refreshUnusedAppRestrictionStatus()
    }

    private fun refreshNotificationAccessState() {
        val ctx: Context = getApplication()
        val s = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        val notificationAccessOn = s != null && s.contains(ctx.packageName)
        _uiState.getAndUpdate { state ->
            state.takeIf { it.notificationAccess == notificationAccessOn }
                ?: state.copy(notificationAccess = notificationAccessOn)
        }
    }

    private fun refreshAccessibilityState() {
        val ctx: Context = getApplication()
        val accessibilityOn = isAccessibilitySettingsOn(ctx)
        _uiState.getAndUpdate { state ->
            state.takeIf { it.accessibility == accessibilityOn }
                ?: state.copy(accessibility = accessibilityOn)
        }
    }

    private fun isAccessibilitySettingsOn(context: Context): Boolean {
        val service =
            context.packageName + "/" + AccessibilityMonitorService::class.java.canonicalName
        val accessibilityEnabled = try {
            Settings.Secure.getInt(
                context.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            e.printStackTrace()
            0
        }
        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(service, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun refreshIgnoreBatteryOptimizationState() {
        val ctx: Context = getApplication()
        val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignore = powerManager.isIgnoringBatteryOptimizations(ctx.packageName)
        _uiState.getAndUpdate { state ->
            state.takeIf { it.ignoreBatteryOptimize == ignore }
                ?: state.copy(ignoreBatteryOptimize = ignore)
        }
    }

    private suspend fun refreshUnusedAppRestrictionStatus() {
        val unusedAppRestrictionsStatus: Boolean? =
            when (PackageManagerCompat.getUnusedAppRestrictionsStatus(getApplication()).await()) {
                UnusedAppRestrictionsConstants.ERROR,
                UnusedAppRestrictionsConstants.FEATURE_NOT_AVAILABLE -> null

                UnusedAppRestrictionsConstants.DISABLED -> false
                UnusedAppRestrictionsConstants.API_30_BACKPORT,
                UnusedAppRestrictionsConstants.API_30,
                UnusedAppRestrictionsConstants.API_31 -> true

                else -> null
            }
        _uiState.getAndUpdate { it.copy(unusedAppRestrictionsEnabled = unusedAppRestrictionsStatus) }
    }

}