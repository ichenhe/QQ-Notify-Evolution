package cc.chenhe.qqnotifyevo.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.preference.PreferenceManager
import cc.chenhe.qqnotifyevo.R

enum class Mode(val v: Int, @StringRes val strId: Int) {
    Nevo(1, R.string.pref_mode_nevo),
    Legacy(2, R.string.pref_mode_legacy);

    companion object {
        fun fromValue(v: Int?): Mode {
            return Mode.values().firstOrNull { it.v == v } ?: Legacy
        }
    }
}

enum class IconStyle(val v: Int, @StringRes val strId: Int) {
    Auto(0, R.string.pref_icon_mode_auto),
    QQ(1, R.string.pref_icon_mode_qq),
    TIM(2, R.string.pref_icon_mode_tim);

    companion object {
        fun fromValue(v: Int?): IconStyle {
            return values().firstOrNull { it.v == v } ?: Auto
        }
    }
}

enum class SpecialGroupChannel(val v: String, @StringRes val strId: Int) {
    Group("group", R.string.pref_advanced_special_group_channel_group),
    Special("special", R.string.pref_advanced_special_group_channel_special);

    companion object {
        fun fromValue(v: String?): SpecialGroupChannel {
            return values().firstOrNull { it.v == v } ?: PREFERENCE_SPECIAL_GROUP_CHANNEL_DEFAULT
        }
    }
}

enum class AvatarCacheAge(val v: Long, @StringRes val strId: Int) {
    TenMinute(600000, R.string.pref_acatar_cache_period_10min),
    OneDay(86400000, R.string.pref_acatar_cache_period_1day),
    SevenDay(604800000, R.string.pref_acatar_cache_period_7day);

    companion object {
        fun fromValue(v: Long?): AvatarCacheAge {
            return values().firstOrNull { it.v == v } ?: PREFERENCE_AVATAR_CACHE_AGE_DEFAULT
        }
    }
}


private fun sp(context: Context): SharedPreferences = PreferenceManager
    .getDefaultSharedPreferences(context.createDeviceProtectedStorageContext())

// ---------------------------------------------------------
// Tips
// ---------------------------------------------------------
private const val PREF_NEVO_MULTI_MSG_TIP = "tip_nevo_multi_msg"


fun nevoMultiMsgTip(context: Context, shouldShow: Boolean) {
    sp(context).edit {
        putBoolean(PREF_NEVO_MULTI_MSG_TIP, shouldShow)
    }
}

fun nevoMultiMsgTip(context: Context): Boolean =
    sp(context).getBoolean(PREF_NEVO_MULTI_MSG_TIP, true)


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val PREFERENCE_MODE = intPreferencesKey("mode")
val PREFERENCE_ICON = intPreferencesKey("icon")
val PREFERENCE_SHOW_SPECIAL_PREFIX = booleanPreferencesKey("show_special_prefix")
const val PREFERENCE_SHOW_SPECIAL_PREFIX_DEFAULT = false
val PREFERENCE_SPECIAL_GROUP_CHANNEL = stringPreferencesKey("special_in_group_channel")
val PREFERENCE_SPECIAL_GROUP_CHANNEL_DEFAULT = SpecialGroupChannel.Group
val PREFERENCE_FORMAT_NICKNAME = booleanPreferencesKey("format_nickname")
const val PREFERENCE_FORMAT_NICKNAME_DEFAULT = false
val PREFERENCE_NICKNAME_FORMAT = stringPreferencesKey("format_nickname_format")
const val PREFERENCE_NICKNAME_FORMAT_DEFAULT = "[\$n]"
val PREFERENCE_AVATAR_CACHE_AGE = longPreferencesKey("avatar_cache_age")
val PREFERENCE_AVATAR_CACHE_AGE_DEFAULT = AvatarCacheAge.OneDay
val USAGE_TIP_NEVO_MULTI_MESSAGE = booleanPreferencesKey("show_nevo_multi_message_tip")
const val USAGE_TIP_NEVO_MULTI_MESSAGE_DEFAULT = true
val PREFERENCE_SHOW_IN_RECENT_APPS = booleanPreferencesKey("show_in_recent_apps")
const val PREFERENCE_SHOW_IN_RECENT_APPS_DEFAULT = true
val PREFERENCE_ENABLE_LOG = booleanPreferencesKey("enable_log")
const val PREFERENCE_ENABLE_LOG_DEFAULT = false


fun getAvatarCachePeriod(context: Context): Long {
    val s = sp(context).getString("avatar_cache_period", "0") ?: "0"
    return s.toLong()
}

fun getVersion(context: Context): String {
    var versionName = ""
    var versionCode = 0L
    var isApkInDebug = false
    try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        versionName = pi?.versionName ?: "UNKNOWN"
        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi?.longVersionCode ?: 0
        } else {
            @Suppress("DEPRECATION")
            pi?.versionCode?.toLong() ?: 0
        }
        val info = context.applicationInfo
        isApkInDebug = info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return "$versionName-${if (isApkInDebug) "debug" else "release"}($versionCode)"
}
