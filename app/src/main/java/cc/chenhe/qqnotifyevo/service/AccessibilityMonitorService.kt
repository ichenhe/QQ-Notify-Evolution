package cc.chenhe.qqnotifyevo.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import cc.chenhe.qqnotifyevo.core.NotificationProcessor
import timber.log.Timber

class AccessibilityMonitorService : AccessibilityService() {
    companion object {
        const val TAG = "Accessibility"
    }

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).v("Service - onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag(TAG).v("Service - onDestroy")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            return
        if (event.packageName == null || event.className == null)
            return
        val tag = NotificationProcessor.getTagFromPackageName(event.packageName.toString())
        val className = event.className.toString()
        if ("com.tencent.mobileqq.activity.SplashActivity" == event.className ||
            "com.dataline.activities.LiteActivity" == event.className
        ) {
            Intent(this, NotificationMonitorService::class.java)
                .putExtra("tag", tag.name)
                .also { startService(it) }
        } else if (className.startsWith("cooperation.qzone.")) {
            Intent(this, NotificationMonitorService::class.java)
                .putExtra("tag", tag.name)
                .also { startService(it) }
        }
    }

    override fun onInterrupt() {
        Timber.tag(TAG).w("Service - onInterrupt")
    }

}

