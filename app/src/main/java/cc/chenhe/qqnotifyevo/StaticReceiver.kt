package cc.chenhe.qqnotifyevo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import cc.chenhe.qqnotifyevo.service.NotificationMonitorService
import cc.chenhe.qqnotifyevo.service.UpgradeService
import cc.chenhe.qqnotifyevo.utils.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class StaticReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "StaticReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                runBlocking {
                    val mode = context.applicationContext.dataStore.data.first()[PREFERENCE_MODE]
                    if (Mode.fromValue(mode) == Mode.Legacy) {
                        val start = Intent(context, NotificationMonitorService::class.java)
                        context.startService(start)
                    }
                }
            }

            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Timber.tag(TAG).i("receive broadcast: %s", Intent.ACTION_MY_PACKAGE_REPLACED)
                UpgradeService.startIfNecessary(context)
            }

            ACTION_MULTI_MSG_DONT_SHOW -> {
                NotificationManagerCompat.from(context).cancel(NOTIFY_ID_MULTI_MSG)
                nevoMultiMsgTip(context, false)
            }
        }
    }
}