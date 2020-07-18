package cc.chenhe.qqnotifyevo.service

import android.os.Process
import android.util.Log
import cc.chenhe.qqnotifyevo.core.NotificationProcessor
import cc.chenhe.qqnotifyevo.utils.getNotificationChannels
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification
import com.oasisfeng.nevo.sdk.NevoDecoratorService

class NevoDecorator : NevoDecoratorService() {

    /**
     * 保存已创建过通知渠道的包名，尽力避免多次创建。
     */
    private lateinit var notificationChannelCreated: MutableSet<String>

    private lateinit var processor: NotificationProcessor

    override fun onConnected() {
        super.onConnected()
        Log.w(TAG, "Nevo onConnected")
        notificationChannelCreated = mutableSetOf()
        processor = NotificationProcessor()

        createChannels(null)
    }

    private fun createChannels(packageName: String?) {
        if (!notificationChannelCreated.contains(packageName)) {
            Log.d(TAG, "注册通知渠道 $packageName")
            if (packageName != null) {
                notificationChannelCreated.add(packageName)
                createNotificationChannels(packageName, Process.myUserHandle(), getNotificationChannels(this))
            } else {
                listOf(
                        "com.tencent.mobileqq",
                        "com.tencent.tim",
                        "com.tencent.qqlite"
                ).forEach { pkg ->
                    try {
                        createNotificationChannels(pkg, Process.myUserHandle(), getNotificationChannels(this))
                    } catch (e: SecurityException) {
                        Log.w(TAG, "注册通知渠道异常：" + e.message)
                    }
                }
            }
        }
    }

    override fun apply(evolving: MutableStatusBarNotification?): Boolean {
        if (evolving == null)
            return false

        createChannels(evolving.packageName)

        val newNotification = processor.resolveNotification(this, evolving.packageName, evolving) ?: return false

        val mutable = evolving.notification
        mutable.extras = newNotification.extras
        mutable.channelId = newNotification.channelId
        mutable.number = newNotification.number
        mutable.`when` = newNotification.`when`
        mutable.smallIcon = newNotification.smallIcon
        mutable.color = newNotification.color
        return true
    }
}