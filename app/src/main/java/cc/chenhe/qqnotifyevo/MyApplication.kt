package cc.chenhe.qqnotifyevo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import cc.chenhe.qqnotifyevo.utils.*


class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("MyApplication", "创建通知渠道")

            val att = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

            val friendChannel = NotificationChannel(NOTIFY_FRIEND_CHANNEL_ID,
                    getString(R.string.notify_friend_channel_name),
                    NotificationManager.IMPORTANCE_HIGH)
            friendChannel.description = getString(R.string.notify_friend_channel_des)
            friendChannel.setSound(getRingtone(this, NotifyChannel.FRIEND), att)
            friendChannel.enableVibration(isVibrate(this, NotifyChannel.FRIEND))

            val groupChannel = NotificationChannel(NOTIFY_GROUP_CHANNEL_ID,
                    getString(R.string.notify_group_channel_name),
                    NotificationManager.IMPORTANCE_HIGH)
            groupChannel.description = getString(R.string.notify_group_channel_des)
            groupChannel.setSound(getRingtone(this, NotifyChannel.GROUP), att)
            groupChannel.enableVibration(isVibrate(this, NotifyChannel.GROUP))

            val qzoneChannel = NotificationChannel(NOTIFY_QZONE_CHANNEL_ID,
                    getString(R.string.notify_qzone_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT)
            qzoneChannel.description = getString(R.string.notify_qzone_channel_des)
            qzoneChannel.setSound(getRingtone(this, NotifyChannel.QZONE), att)
            qzoneChannel.enableVibration(isVibrate(this, NotifyChannel.QZONE))

            (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.run {
                createNotificationChannel(friendChannel)
                createNotificationChannel(groupChannel)
                createNotificationChannel(qzoneChannel)
            }
        }
    }

}
