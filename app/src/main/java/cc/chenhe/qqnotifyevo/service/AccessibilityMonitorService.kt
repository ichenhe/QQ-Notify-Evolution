package cc.chenhe.qqnotifyevo.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AccessibilityMonitorService : AccessibilityService() {
    companion object {
        const val TAG = "Accessibility"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            return
        if (event.packageName == null || event.className == null)
            return
        val tag = NotificationMonitorService.getTagFromPackageName(event.packageName.toString())
        val className = event.className.toString()
        Log.v(TAG, className)
        if ("com.tencent.mobileqq.activity.SplashActivity" == event.className ||
                "com.dataline.activities.LiteActivity" == event.className) {
            startService(Intent(this, NotificationMonitorService::class.java).putExtra("tag", tag))
        } else if (className.startsWith("cooperation.qzone.")) {
            startService(Intent(this, NotificationMonitorService::class.java).putExtra("tag", NotificationMonitorService.TAG_QZONE))
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务关闭")
    }

}

