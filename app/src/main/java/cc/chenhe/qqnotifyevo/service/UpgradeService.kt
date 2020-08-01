package cc.chenhe.qqnotifyevo.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.os.UserManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.utils.ACTION_APPLICATION_UPGRADE_COMPLETE
import cc.chenhe.qqnotifyevo.utils.NOTIFY_ID_UPGRADE
import cc.chenhe.qqnotifyevo.utils.NOTIFY_SELF_FOREGROUND_SERVICE_CHANNEL_ID
import cc.chenhe.qqnotifyevo.utils.UpgradeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Suppress("FunctionName")
class UpgradeService : Service() {

    companion object {

        private const val TAG = "UpgradeService"

        private const val EXTRA_OLD_VERSION = "old"
        private const val EXTRA_CURRENT_VERSION = "new"

        private const val VERSION_2_0_2 = 20023

        private var instance: UpgradeService? = null
        private var preparedToRunning = false

        /**
         * 是否正在运行或者正准备运行。
         */
        fun isRunningOrPrepared(): Boolean {
            return preparedToRunning || isRunning()
        }

        private fun isRunning(): Boolean {
            return try {
                // 如果服务被强制结束，标记没有释放，那么此处会抛出异常。
                instance?.ping() ?: false
            } catch (e: Exception) {
                false
            }
        }

        fun startIfNecessary(context: Context): Boolean {
            val old: Long = UpgradeUtils.getOldVersion(context)
            val new: Long = UpgradeUtils.getCurrentVersion(context)
            if (old == new) {
                Timber.tag(TAG).d("Old version equals to the current, no need to upgrade. v=$new")
                return false
            } else if (old > new) {
                // should never happen
                Timber.tag(TAG).e("Current version is lower than old version! current=$new, old=$old")
                return false
            }

            // old < new
            return if (shouldPerformUpgrade(old)) {
                preparedToRunning = true
                val i = Intent(context.applicationContext, UpgradeService::class.java).apply {
                    putExtra(EXTRA_OLD_VERSION, old)
                    putExtra(EXTRA_CURRENT_VERSION, new)
                }
                context.startService(i)
                true
            } else {
                Timber.tag(TAG).i("No need to perform data migration, update version code directly $old → $new.")
                UpgradeUtils.setOldVersion(context, new)
                false
            }
        }

        private fun shouldPerformUpgrade(old: Long): Boolean {
            return old in 1..VERSION_2_0_2
        }
    }

    private var running = false

    private lateinit var ctx: Context

    private fun ping() = true

    override fun onCreate() {
        super.onCreate()
        instance = this
        ctx = this.application
        createNotificationChannel()
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(NOTIFY_SELF_FOREGROUND_SERVICE_CHANNEL_ID,
                getString(R.string.notify_self_foreground_service_channel_name),
                NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notify_self_foreground_service_channel_name_des)
        }
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) {
            running = true
            preparedToRunning = false

            val notify = NotificationCompat.Builder(this, NOTIFY_SELF_FOREGROUND_SERVICE_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notify_upgrade)
                    .setContentTitle(getString(R.string.notify_upgrade))
                    .setContentText(getString(R.string.notify_upgrade_text))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
            startForeground(NOTIFY_ID_UPGRADE, notify)

            GlobalScope.launch {
                val old = intent!!.getLongExtra(EXTRA_OLD_VERSION, -10)
                val new = intent.getLongExtra(EXTRA_CURRENT_VERSION, -10)
                if (old == -10L || new == -10L) {
                    Timber.tag(TAG).e("onStartCommand: unknown version. old=$old, new=$new")
                    complete(false, 0)
                } else {
                    startUpgrade(old, new)
                }
            }
        } else {
            preparedToRunning = false
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * 升级完成后调用此函数。
     */
    private fun complete(success: Boolean, currentVersion: Long) {
        if (success) {
            Timber.tag(TAG).d("Upgrade complete.")
            UpgradeUtils.setOldVersion(this, currentVersion)
        } else {
            Timber.tag(TAG).e("Upgrade error!")
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_APPLICATION_UPGRADE_COMPLETE))
        stopForeground(true)
        stopSelf()
    }

    private suspend fun startUpgrade(oldVersion: Long, currentVersion: Long) = withContext(Dispatchers.Main) {
        Timber.tag(TAG).d("Start upgrade process. $oldVersion → $currentVersion")

        if (oldVersion in 1..VERSION_2_0_2) {
            migrate_1_to_2_0_2()
        }

        complete(true, currentVersion)
    }

    private suspend fun migrate_1_to_2_0_2() = withContext(Dispatchers.Main) {
        if (UserManagerCompat.isUserUnlocked(ctx)) {
            Timber.tag(TAG).d("Move default preferences to device protected area.")
            val deviceCtx = ctx.createDeviceProtectedStorageContext()
            deviceCtx.moveSharedPreferencesFrom(ctx, ctx.packageName + "_preferences")
            Timber.tag(TAG).d("Remove deprecated preferences.")
            PreferenceManager.getDefaultSharedPreferences(deviceCtx).edit {
                remove("friend_vibrate")
                remove("friend_ringtone")
                remove("group_notify")
                remove("group_ringtone")
                remove("group_vibrate")
                remove("qzone_notify")
                remove("qzone_ringtone")
                remove("qzone_vibrate")
            }
        }
    }

    override fun onBind(i: Intent?): IBinder? {
        return null
    }
}