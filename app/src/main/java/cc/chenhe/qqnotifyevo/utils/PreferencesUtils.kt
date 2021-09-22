package cc.chenhe.qqnotifyevo.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.IntDef
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.preference.PreferenceManager

// ---------------------------------------------------------
// 工作模式
// ---------------------------------------------------------

/** Nevo 插件 */
const val MODE_NEVO = 1

/** 通过通知访问权、辅助服务等特殊权限替换原生通知 */
const val MODE_LEGACY = 2

@Retention(AnnotationRetention.SOURCE)
@IntDef(MODE_NEVO, MODE_LEGACY)
annotation class Mode

// ---------------------------------------------------------
// 通知图标
// ---------------------------------------------------------

const val ICON_AUTO = 0
const val ICON_QQ = 1
const val ICON_TIM = 2

@Retention(AnnotationRetention.SOURCE)
@IntDef(ICON_AUTO, ICON_QQ, ICON_TIM)
annotation class Icon

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


// ---------------------------------------------------------
// Functions
// ---------------------------------------------------------

@Mode
fun getMode(context: Context): Int {
    val mode = sp(context).getString("mode", "0") ?: "0"
    return when (mode.toInt()) {
        1 -> MODE_NEVO
        2 -> MODE_LEGACY
        else -> MODE_NEVO
    }
}

fun fetchMode(context: Context): LiveData<Int> {
    val source = SpStringLiveData(sp(context), "mode", "0", true)
    return Transformations.map(source) { src ->
        src!!.toInt()
    }
}

@Icon
fun getIconMode(context: Context): Int {
    val icon = sp(context).getString("icon_mode", "0") ?: "0"
    return when (icon.toInt()) {
        0 -> ICON_AUTO
        1 -> ICON_QQ
        2 -> ICON_TIM
        else -> ICON_AUTO
    }
}

fun showSpecialPrefix(context: Context): Boolean =
    sp(context).getBoolean("show_special_prefix", false)

/**
 * 特别关注的群消息通知渠道。
 *
 * @return `true` 为特别关心渠道，`false` 为群消息渠道。
 */
fun specialGroupMsgChannel(context: Context): Boolean =
    sp(context).getString("special_group_channel", "group") == "special"

fun wrapNickname(context: Context): Boolean = sp(context).getBoolean("wrap_nickname", false)

fun nicknameWrapper(context: Context): String? = sp(context).getString("nickname_wrapper", null)

/**
 * 禁用格式化昵称，且将格式重置为默认值。
 */
fun resetNicknameWrapper(context: Context) {
    sp(context).edit {
        putBoolean("wrap_nickname", false)
        putString("nickname_wrapper", "[\$n]")
    }
}

fun getAvatarCachePeriod(context: Context): Long {
    val s = sp(context).getString("avatar_cache_period", "0") ?: "0"
    return s.toLong()
}

fun fetchAvatarCachePeriod(context: Context): LiveData<Long> {
    val source = SpStringLiveData(sp(context), "avatar_cache_period", "0", true)
    return Transformations.map(source) { src ->
        src?.toLong() ?: 0L
    }
}

fun getShowInRecent(context: Context): Boolean {
    return sp(context).getBoolean("show_in_recent", true)
}

fun fetchLog(context: Context): SpBooleanLiveData =
    SpBooleanLiveData(sp(context), "log", false, init = true)

fun getVersion(context: Context): String {
    var versionName = ""
    var versionCode = 0L
    var isApkInDebug = false
    try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        versionName = pi.versionName
        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }
        val info = context.applicationInfo
        isApkInDebug = info.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return "$versionName-${if (isApkInDebug) "debug" else "release"}($versionCode)"
}
