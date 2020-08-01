package cc.chenhe.qqnotifyevo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import cc.chenhe.qqnotifyevo.service.NotificationMonitorService
import cc.chenhe.qqnotifyevo.utils.*

class StaticReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (getMode(context) == MODE_LEGACY) {
                    val start = Intent(context, NotificationMonitorService::class.java)
                    context.startService(start)
                }
            }
            ACTION_MULTI_MSG_DONT_SHOW -> {
                NotificationManagerCompat.from(context).cancel(NOTIFY_ID_MULTI_MSG)
                nevoMultiMsgTip(context, false)
            }
        }
    }
}