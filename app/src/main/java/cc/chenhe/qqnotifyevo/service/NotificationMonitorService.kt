package cc.chenhe.qqnotifyevo.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import cc.chenhe.qqnotifyevo.core.InnerNotificationProcessor
import cc.chenhe.qqnotifyevo.utils.MODE_LEGACY
import cc.chenhe.qqnotifyevo.utils.getMode

class NotificationMonitorService : NotificationListenerService(), InnerNotificationProcessor.Commander {

    companion object {
        var instance: NotificationMonitorService? = null

        fun isRunning(): Boolean {
            return try {
                // 如果服务被强制结束，标记没有释放，那么此处会抛出异常。
                instance?.ping() ?: false
            } catch (e: Exception) {
                false
            }
        }
    }

    private lateinit var ctx: Context

    private lateinit var processor: InnerNotificationProcessor

    override fun onCreate() {
        super.onCreate()
        instance = this
        ctx = this
        processor = InnerNotificationProcessor(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    private fun ping() = true

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (getMode(this) != MODE_LEGACY)
            return
        processor.resolveNotification(ctx, sbn.packageName, sbn)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (getMode(this) != MODE_LEGACY)
            return Service.START_STICKY
        if (intent?.hasExtra("tag") == true) {
            processor.clearHistory(ctx, intent.getIntExtra("tag", 0))
        }
        return Service.START_STICKY
    }

}

