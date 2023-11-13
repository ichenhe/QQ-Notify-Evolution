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
import androidx.lifecycle.lifecycleScope
import cc.chenhe.qqnotifyevo.core.InnerNotificationProcessor
import cc.chenhe.qqnotifyevo.utils.Mode
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_MODE
import cc.chenhe.qqnotifyevo.utils.Tag
import cc.chenhe.qqnotifyevo.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private lateinit var mode: Mode

    private lateinit var processor: InnerNotificationProcessor

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        instance = this
        ctx = this
        Timber.tag(TAG).v("Service - onCreate")
        lifecycleRegistry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
        mode = runBlocking {
            Mode.fromValue(ctx.dataStore.data.first()[PREFERENCE_MODE])
        }
        lifecycleScope.launch {
            ctx.dataStore.data.collect { pref ->
                mode = Mode.fromValue(pref[PREFERENCE_MODE])
            }
        }

        processor = InnerNotificationProcessor(this, this, lifecycleScope)
    }

    override fun onBind(intent: Intent?): IBinder? {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        return super.onBind(intent)
    }

    @Deprecated("Deprecated in Android")
    override fun onStart(intent: Intent?, startId: Int) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        @Suppress("DEPRECATION")
        super.onStart(intent, startId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (mode != Mode.Legacy) {
            return Service.START_STICKY
        }
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

        if (mode != Mode.Legacy) {
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
        if (sbn == null || mode != Mode.Legacy) {
            return
        }
        processor.onNotificationRemoved(sbn, reason)
        super.onNotificationRemoved(sbn, rankingMap, reason)
    }

}

