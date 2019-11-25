package cc.chenhe.qqnotifyevo.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.preference.PreferenceManager

fun getIconMode(context: Context): String {
    return PreferenceManager.getDefaultSharedPreferences(context)
            .getString("icon_mode", "0")?:"0"
}

fun getRingtone(context: Context, channel: NotifyChannel): Uri? {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    val uri = when (channel) {
        NotifyChannel.FRIEND -> sp.getString("friend_ringtone", RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString())
        NotifyChannel.GROUP -> sp.getString("group_ringtone",  RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString())
        NotifyChannel.QZONE -> sp.getString("qzone_ringtone",  RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString())
    }
    return uri?.let { Uri.parse(uri) }
}

fun isVibrate(context: Context, channel: NotifyChannel): Boolean {
    val key = when (channel) {
        NotifyChannel.FRIEND -> "friend_vibrate"
        NotifyChannel.GROUP -> "group_vibrate"
        NotifyChannel.QZONE -> "qzone_vibrate"
    }
    return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(key, false)
}

/**
 * 群组消息是否显示通知。
 */
fun isGroupNotify(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("group_notify", true)
}

/**
 * QQ空间消息是否显示通知。
 */
fun isQzoneNotify(context: Context): Boolean {
    return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean("qzone_notify", true)
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
        }else{
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
