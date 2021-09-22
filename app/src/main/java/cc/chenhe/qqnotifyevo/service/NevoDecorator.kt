package cc.chenhe.qqnotifyevo.service

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import cc.chenhe.qqnotifyevo.core.NevoNotificationProcessor
import cc.chenhe.qqnotifyevo.utils.*
import com.oasisfeng.nevo.sdk.MutableStatusBarNotification
import com.oasisfeng.nevo.sdk.NevoDecoratorService
import timber.log.Timber

class NevoDecorator : NevoDecoratorService(), LifecycleOwner {

    companion object {
        private var instance: NevoDecorator? = null

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

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    @SuppressLint("WrongThread") // wrong report
    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.tag(TAG).v("Service - onCreate")
        lifecycleRegistry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }

        receiver = Receiver()
        registerReceiver(receiver, IntentFilter(ACTION_DELETE_NEVO_CHANNEL))

        processor = NevoNotificationProcessor(this)
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
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Timber.tag(TAG).v("Service - onDestroy")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
        instance = null
        if (::receiver.isInitialized) {
            unregisterReceiver(receiver)
        }
    }

    private fun ping() = true

    override fun onConnected() {
        super.onConnected()
        Timber.tag(TAG).d("Nevo connected")
        notificationChannelCreated = mutableSetOf()

        if (getMode(this) == MODE_NEVO) {
            createChannels(null)
        }
    }

    private fun deleteChannels() {
        packageNameList.forEach out@{ pkg ->
            notificationChannelIdList.forEach { id ->
                try {
                    deleteNotificationChannel(pkg, Process.myUserHandle(), id)
                    Timber.tag(TAG).d("Delete nevo notification channel, pkg=$pkg, channelId=$id")
                } catch (e: SecurityException) {
                    return@out
                }
            }
        }
    }

    private fun createChannels(packageName: String?) {
        if (notificationChannelCreated.contains(packageName)) {
            return
        }
        Timber.tag(TAG).d("Register nevo notification channel for ${packageName ?: "All"}")
        if (packageName != null) {
            notificationChannelCreated.add(packageName)
            createNotificationChannels(
                packageName,
                Process.myUserHandle(),
                getNotificationChannels(this, true)
            )
        } else {
            packageNameList.forEach { pkg ->
                try {
                    createNotificationChannels(
                        pkg,
                        Process.myUserHandle(),
                        getNotificationChannels(this, true)
                    )
                } catch (e: SecurityException) {
                    Timber.tag(TAG).w(e, "Register nevo notification channel error.")
                }
            }
        }
    }

    override fun apply(evolving: MutableStatusBarNotification?): Boolean {
        Timber.tag(TAG).v("Detect notification from ${evolving?.packageName}.")
        if (getMode(this) != MODE_NEVO) {
            Timber.tag(TAG).d("Not in nevo mode, skip.")
            return false
        }
        if (evolving == null) {
            Timber.tag(TAG).w("<evolving> is null, skip.")
            return false
        }

        createChannels(evolving.packageName)

        val newNotification = processor.resolveNotification(this, evolving.packageName, evolving)
        if (newNotification == null) {
            Timber.tag(TAG).i("No need to evolve, skip.")
            return false
        }

        val mutable = evolving.notification
        mutable.extras = newNotification.extras
        mutable.channelId = newNotification.channelId
        mutable.number = newNotification.number
        mutable.`when` = newNotification.`when`
        mutable.smallIcon = newNotification.smallIcon
        mutable.color = newNotification.color
        return true
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, reason: Int): Boolean {
        if (sbn == null || getMode(this) != MODE_NEVO) return false
        processor.onNotificationRemoved(sbn, reason)
        return false
    }
}