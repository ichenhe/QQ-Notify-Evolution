package cc.chenhe.qqnotifyevo.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import cc.chenhe.qqnotifyevo.core.InnerNotificationProcessor
import cc.chenhe.qqnotifyevo.utils.MODE_LEGACY
import cc.chenhe.qqnotifyevo.utils.Tag
import cc.chenhe.qqnotifyevo.utils.fetchAvatarCachePeriod
import cc.chenhe.qqnotifyevo.utils.getMode
import timber.log.Timber

class NotificationMonitorService : NotificationListenerService(),
    InnerNotificationProcessor.Commander,
    LifecycleOwner {

    companion object {
        private const val TAG = "NotifyMonitor"

        @SuppressLint("StaticFieldLeak")
        private var instance: NotificationMonitorService? = null

        fun isRunning(): Boolean {
            return try {
                // 如果服务被强制结束，标记没有释放，那么此处会抛出异常。
                instance?.ping() ?: false
            } catch (e: Exception) {
                false
            }
        }
    }

    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var ctx: Context

    private lateinit var processor: InnerNotificationProcessor

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        instance = this
        ctx = this
        Timber.tag(TAG).v("Service - onCreate")
        lifecycleRegistry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
        processor = InnerNotificationProcessor(this, this)
        fetchAvatarCachePeriod(this).observe(this) { avatarCachePeriod ->
            processor.setAvatarCachePeriod(avatarCachePeriod)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        return super.onBind(intent)
    }

    override fun onStart(intent: Intent?, startId: Int) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        @Suppress("DEPRECATION")
        super.onStart(intent, startId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (getMode(this) != MODE_LEGACY)
            return Service.START_STICKY
        if (intent?.hasExtra("tag") == true) {
            (intent.getStringExtra("tag") ?: Tag.UNKNOWN.name)
                .let { Tag.valueOf(it) }
                .also { processor.clearHistory(ctx, it) }
        }
        return Service.START_STICKY
    }

    override fun onDestroy() {
        Timber.tag(TAG).v("Service - onDestroy")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
        instance = null
    }

    private fun ping() = true

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Timber.tag(TAG).v("Detect notification from ${sbn.packageName}.")

        if (getMode(this) != MODE_LEGACY) {
            Timber.tag(TAG).d("Not in legacy mode, skip.")
            return
        }
        processor.resolveNotification(ctx, sbn.packageName, sbn)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int
    ) {
        if (sbn == null || getMode(this) != MODE_LEGACY) return
        processor.onNotificationRemoved(sbn, reason)
        super.onNotificationRemoved(sbn, rankingMap, reason)
    }

}

