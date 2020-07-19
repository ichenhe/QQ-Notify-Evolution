package cc.chenhe.qqnotifyevo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.util.Log
import cc.chenhe.qqnotifyevo.core.NevoNotificationProcessor
import cc.chenhe.qqnotifyevo.utils.*
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification
import com.oasisfeng.nevo.sdk.NevoDecoratorService

class NevoDecorator : NevoDecoratorService() {

    companion object {
        var instance: NevoDecorator? = null

        fun isRunning(): Boolean {
            return try {
                // 如果服务被强制结束，标记没有释放，那么此处会抛出异常。
                instance?.ping() ?: false
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 保存已创建过通知渠道的包名，尽力避免多次创建。
     */
    private lateinit var notificationChannelCreated: MutableSet<String>

    private lateinit var processor: NevoNotificationProcessor
    private lateinit var receiver: Receiver

    private inner class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, i: Intent?) {
            if (i?.action == ACTION_DELETE_NEVO_CHANNEL) {
                deleteChannels()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        receiver = Receiver()
        registerReceiver(receiver, IntentFilter(ACTION_DELETE_NEVO_CHANNEL))
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (::receiver.isInitialized) {
            unregisterReceiver(receiver)
        }
    }

    private fun ping() = true

    override fun onConnected() {
        super.onConnected()
        notificationChannelCreated = mutableSetOf()
        processor = NevoNotificationProcessor()

        if (getMode(this) == MODE_NEVO) {
            createChannels(null)
        }
    }

    private fun deleteChannels() {
        packageNameList.forEach out@{ pkg ->
            notificationChannelIdList.forEach { id ->
                try {
                    deleteNotificationChannel(pkg, Process.myUserHandle(), id)
                    Log.d(TAG, "已删除 Nevo 通知渠道, pkg=$pkg, id=$id")
                } catch (e: SecurityException) {
                    return@out
                }
            }
        }
    }

    private fun createChannels(packageName: String?) {
        if (!notificationChannelCreated.contains(packageName)) {
            Log.d(TAG, "注册通知渠道 ${packageName ?: "All"}")
            if (packageName != null) {
                notificationChannelCreated.add(packageName)
                createNotificationChannels(packageName, Process.myUserHandle(), getNotificationChannels(this, true))
            } else {
                packageNameList.forEach { pkg ->
                    try {
                        createNotificationChannels(pkg, Process.myUserHandle(), getNotificationChannels(this, true))
                    } catch (e: SecurityException) {
                        Log.w(TAG, "注册通知渠道异常：" + e.message)
                    }
                }
            }
        }
    }

    override fun apply(evolving: MutableStatusBarNotification?): Boolean {
        if (getMode(this) != MODE_NEVO || evolving == null)
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