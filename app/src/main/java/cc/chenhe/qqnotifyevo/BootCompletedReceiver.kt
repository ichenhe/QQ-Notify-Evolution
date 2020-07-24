package cc.chenhe.qqnotifyevo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cc.chenhe.qqnotifyevo.service.NotificationMonitorService
import cc.chenhe.qqnotifyevo.utils.MODE_LEGACY
import cc.chenhe.qqnotifyevo.utils.getMode

/**
 * 系统启动完成广播接收器
 * #<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"></uses-permission>
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (getMode(context) == MODE_LEGACY) {
                val start = Intent(context, NotificationMonitorService::class.java)
                context.startService(start)
            }
        }
    }
}