package cc.chenhe.qqnotifyevo.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.os.UserManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.utils.ACTION_APPLICATION_UPGRADE_COMPLETE
import cc.chenhe.qqnotifyevo.utils.AvatarCacheAge
import cc.chenhe.qqnotifyevo.utils.IconStyle
import cc.chenhe.qqnotifyevo.utils.Mode
import cc.chenhe.qqnotifyevo.utils.NOTIFY_ID_UPGRADE
import cc.chenhe.qqnotifyevo.utils.NOTIFY_SELF_FOREGROUND_SERVICE_CHANNEL_ID
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_AVATAR_CACHE_AGE
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_ENABLE_LOG
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_ENABLE_LOG_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_FORMAT_NICKNAME
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_FORMAT_NICKNAME_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_ICON
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_MODE
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_NICKNAME_FORMAT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_NICKNAME_FORMAT_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SHOW_IN_RECENT_APPS
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SHOW_IN_RECENT_APPS_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SHOW_SPECIAL_PREFIX
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SHOW_SPECIAL_PREFIX_DEFAULT
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SPECIAL_GROUP_CHANNEL
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_SPECIAL_GROUP_CHANNEL_DEFAULT
import cc.chenhe.qqnotifyevo.utils.SpecialGroupChannel
import cc.chenhe.qqnotifyevo.utils.UpgradeUtils
import cc.chenhe.qqnotifyevo.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Suppress("FunctionName")
class UpgradeService : LifecycleService() {

    companion object {

        private const val TAG = "UpgradeService"

        private const val EXTRA_OLD_VERSION = "old"
        private const val EXTRA_CURRENT_VERSION = "new"

        private const val VERSION_2_0_2 = 20023
        private const val VERSION_2_2_6 = 20043

        @SuppressLint("StaticFieldLeak")
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
                Timber.tag(TAG)
                    .e("Current version is lower than old version! current=$new, old=$old")
                return false
            }

            // old < new
            return if (shouldPerformUpgrade(old)) {
                preparedToRunning = true
                val i = Intent(context.applicationContext, UpgradeService::class.java).apply {
                    putExtra(EXTRA_OLD_VERSION, old)
                    putExtra(EXTRA_CURRENT_VERSION, new)
                }
                context.startForegroundService(i)
                true
            } else {
                Timber.tag(TAG)
                    .i("No need to perform data migration, update version code directly $old → $new.")
                UpgradeUtils.setOldVersion(context, new)
                false
            }
        }

        private fun shouldPerformUpgrade(old: Long): Boolean {
            return old in 1..VERSION_2_2_6
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
        val channel = NotificationChannel(
            NOTIFY_SELF_FOREGROUND_SERVICE_CHANNEL_ID,
            getString(R.string.notify_self_foreground_service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
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

            lifecycleScope.launch {
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
        LocalBroadcastManager.getInstance(this)
            .sendBroadcast(Intent(ACTION_APPLICATION_UPGRADE_COMPLETE))
        stopSelf()
    }

    private suspend fun startUpgrade(oldVersion: Long, currentVersion: Long) =
        withContext(Dispatchers.Main) {
            Timber.tag(TAG).d("Start upgrade process. $oldVersion → $currentVersion")

            if (oldVersion in 1..VERSION_2_0_2) {
                migrate_1_to_2_0_2()
            }
            if (oldVersion <= VERSION_2_2_6) {
                migrateFrom_2_2_6()
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

    private suspend fun migrateFrom_2_2_6() {
        val sp = PreferenceManager
            .getDefaultSharedPreferences(ctx.createDeviceProtectedStorageContext())
        ctx.dataStore.edit { prefs ->
            if (sp.contains("mode")) {
                prefs[PREFERENCE_MODE] = when (sp.getString("mode", null)?.toIntOrNull()) {
                    1 -> Mode.Nevo
                    2 -> Mode.Legacy
                    else -> Mode.Nevo
                }.v
            }
            if (sp.contains("icon_mode")) {
                prefs[PREFERENCE_ICON] = when (sp.getString("icon_mode", null)?.toIntOrNull()) {
                    0 -> IconStyle.Auto
                    1 -> IconStyle.QQ
                    2 -> IconStyle.TIM
                    else -> IconStyle.Auto
                }.v
            }
            if (sp.contains("show_special_prefix")) {
                prefs[PREFERENCE_SHOW_SPECIAL_PREFIX] =
                    sp.getBoolean("show_special_prefix", PREFERENCE_SHOW_SPECIAL_PREFIX_DEFAULT)
            }
            if (sp.contains("special_group_channel")) {
                prefs[PREFERENCE_SPECIAL_GROUP_CHANNEL] =
                    when (sp.getString("special_group_channel", "")) {
                        "group" -> SpecialGroupChannel.Group
                        "special" -> SpecialGroupChannel.Special
                        else -> PREFERENCE_SPECIAL_GROUP_CHANNEL_DEFAULT
                    }.v
            }
            if (sp.contains("wrap_nickname")) {
                prefs[PREFERENCE_FORMAT_NICKNAME] =
                    sp.getBoolean("wrap_nickname", PREFERENCE_FORMAT_NICKNAME_DEFAULT)
            }
            if (sp.contains("nickname_wrapper")) {
                prefs[PREFERENCE_NICKNAME_FORMAT] =
                    sp.getString("nickname_wrapper", PREFERENCE_NICKNAME_FORMAT_DEFAULT)
                        ?: PREFERENCE_NICKNAME_FORMAT_DEFAULT
            }
            if (sp.contains("avatar_cache_period")) {
                val old = sp.getString("avatar_cache_period", null)?.toLongOrNull()
                prefs[PREFERENCE_AVATAR_CACHE_AGE] = AvatarCacheAge.fromValue(old).v
            }
            if (sp.contains("show_in_recent")) {
                prefs[PREFERENCE_SHOW_IN_RECENT_APPS] =
                    sp.getBoolean("show_in_recent", PREFERENCE_SHOW_IN_RECENT_APPS_DEFAULT)
            }
            if (sp.contains("log")) {
                prefs[PREFERENCE_ENABLE_LOG] = sp.getBoolean("log", PREFERENCE_ENABLE_LOG_DEFAULT)
            }
        }
        sp.edit().clear().apply()
    }

}