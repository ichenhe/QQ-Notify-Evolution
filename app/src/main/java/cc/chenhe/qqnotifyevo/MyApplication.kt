package cc.chenhe.qqnotifyevo

import android.app.Application
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import cc.chenhe.qqnotifyevo.log.CrashHandler
import cc.chenhe.qqnotifyevo.log.ReleaseTree
import cc.chenhe.qqnotifyevo.utils.NOTIFY_GROUP_ID
import cc.chenhe.qqnotifyevo.utils.getLogDir
import cc.chenhe.qqnotifyevo.utils.getNotificationChannels
import timber.log.Timber


class MyApplication : Application() {

    companion object {
        private const val TAG = "Application"
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree(getLogDir(this)))
        }
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
        Timber.tag(TAG).i("\n\n")
        Timber.tag(TAG).i("==================================================")
        Timber.tag(TAG).i("= App Create")
        Timber.tag(TAG).i("==================================================\n")
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Timber.tag(TAG).d("Register system notification channels")
            val group = NotificationChannelGroup(NOTIFY_GROUP_ID, getString(R.string.notify_group_base))

            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.apply {
                createNotificationChannelGroup(group)
                for (channel in getNotificationChannels(this@MyApplication, false)) {
                    channel.group = group.id
                    createNotificationChannel(channel)
                }
            }
        }
    }

}
