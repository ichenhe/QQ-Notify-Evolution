package cc.chenhe.qqnotifyevo

import android.app.Application
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import cc.chenhe.qqnotifyevo.utils.NOTIFY_GROUP_ID
import cc.chenhe.qqnotifyevo.utils.getNotificationChannels


class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("MyApplication", "注册系统通知渠道")
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
