package cc.chenhe.qqnotifyevo.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.IntDef
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

// ---------------------------------------------------------
// Functions
// ---------------------------------------------------------

@Mode
fun getMode(context: Context): Int {
    val mode = PreferenceManager.getDefaultSharedPreferences(context).getString("mode", "0") ?: "0"
    return when (mode.toInt()) {
        1 -> MODE_NEVO
        2 -> MODE_LEGACY
        else -> MODE_NEVO
    }
}

fun fetchMode(context: Context): LiveData<Int> {
    val source = SpStringLiveData(PreferenceManager.getDefaultSharedPreferences(context), "mode", "0", true)
    return Transformations.map(source) { src ->
        src!!.toInt()
    }
}

@Icon
fun getIconMode(context: Context): Int {
    val icon = PreferenceManager.getDefaultSharedPreferences(context).getString("icon_mode", "0") ?: "0"
    return when (icon.toInt()) {
        0 -> ICON_AUTO
        1 -> ICON_QQ
        2 -> ICON_TIM
        else -> ICON_AUTO
    }
}

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
