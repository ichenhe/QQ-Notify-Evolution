package cc.chenhe.qqnotifyevo

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationManagerCompat
import cc.chenhe.qqnotifyevo.log.CrashHandler
import cc.chenhe.qqnotifyevo.log.ReleaseTree
import cc.chenhe.qqnotifyevo.service.UpgradeService
import cc.chenhe.qqnotifyevo.utils.*
import timber.log.Timber


class MyApplication : Application() {

    companion object {
        private const val TAG = "Application"
    }

    private lateinit var isLog: SpBooleanLiveData

    private var debugTree: Timber.DebugTree? = null
    private var releaseTree: ReleaseTree? = null

    override fun onCreate() {
        super.onCreate()
        isLog = fetchLog(this)
        setupTimber(isLog.value!!)
        isLog.observeForever { log ->
            setupTimber(log)
        }

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
        Timber.tag(TAG).i("\n\n")
        Timber.tag(TAG).i("==================================================")
        Timber.tag(TAG).i("= App Create")
        Timber.tag(TAG).i("==================================================\n")
        registerNotificationChannel()

        UpgradeService.startIfNecessary(this)
    }

    private fun setupTimber(enableLog: Boolean) {
        if (BuildConfig.DEBUG) {
            if (debugTree == null)
                debugTree = Timber.DebugTree()
            plantIfNotExist(debugTree!!)
        }
        if (enableLog) {
            if (releaseTree == null)
                releaseTree = ReleaseTree(getLogDir(this))
            plantIfNotExist(releaseTree!!)
        } else {
            releaseTree?.also { r ->
                Timber.uproot(r)
                r.close()
                releaseTree = null
            }
        }
    }

    private fun plantIfNotExist(tree: Timber.Tree) {
        if (!Timber.forest().contains(tree))
            Timber.plant(tree)
    }

    fun deleteLog() {
        releaseTree?.close()
        releaseTree = null
        getLogDir(this).deleteRecursively()
        setupTimber(isLog.value!!)
    }

    private fun registerNotificationChannel() {
        Timber.tag(TAG).d("Register system notification channels")
        val group = NotificationChannelGroup(NOTIFY_QQ_GROUP_ID, getString(R.string.notify_group_base))


        val att = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        val tipChannel = NotificationChannel(NOTIFY_SELF_TIPS_CHANNEL_ID,
                getString(R.string.notify_self_tips_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), att)
        }

        NotificationManagerCompat.from(this).apply {
            createNotificationChannelGroup(group)
            for (channel in getNotificationChannels(this@MyApplication, false)) {
                channel.group = group.id
                createNotificationChannel(channel)
            }
            createNotificationChannel(tipChannel)
        }
    }

}
