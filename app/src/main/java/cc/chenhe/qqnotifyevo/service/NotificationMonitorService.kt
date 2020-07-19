package cc.chenhe.qqnotifyevo.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import cc.chenhe.qqnotifyevo.core.InnerNotificationProcessor

class NotificationMonitorService : NotificationListenerService(), InnerNotificationProcessor.Commander {

    private lateinit var ctx: Context

    private lateinit var processor: InnerNotificationProcessor

    override fun onCreate() {
        super.onCreate()
        ctx = this
        processor = InnerNotificationProcessor(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processor.resolveNotification(ctx, sbn.packageName, sbn)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra("tag") == true) {
            processor.clearHistory(ctx, intent.getIntExtra("tag", 0))
        }
        return Service.START_STICKY
    }

}

