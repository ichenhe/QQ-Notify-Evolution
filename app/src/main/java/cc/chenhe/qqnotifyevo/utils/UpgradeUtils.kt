package cc.chenhe.qqnotifyevo.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit

object UpgradeUtils {

    private const val NAME = "upgrade"
    private const val ITEM_OLD_VERSION = "oldVersion"

    private fun Context.deviceProtected(): Context {
        return if (isDeviceProtectedStorage)
            this
        else
            createDeviceProtectedStorageContext()
    }

    private fun deviceProtectedSp(context: Context, name: String = NAME): SharedPreferences {
        return context.deviceProtected().getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    fun getOldVersion(context: Context): Long {
        return deviceProtectedSp(context).getLong(ITEM_OLD_VERSION, 0L)
    }

    fun setOldVersion(context: Context, value: Long) {
        deviceProtectedSp(context).edit {
            putLong(ITEM_OLD_VERSION, value)
        }
    }

    fun getCurrentVersion(context: Context): Long {
        try {
            val pi = context.deviceProtected().packageManager.getPackageInfo(context.packageName, 0)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return 0L
    }
}