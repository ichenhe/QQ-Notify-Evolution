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
import cc.chenhe.qqnotifyevo.utils.NOTIFY_QQ_GROUP_ID
import cc.chenhe.qqnotifyevo.utils.NOTIFY_SELF_TIPS_CHANNEL_ID
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_ENABLE_LOG
import cc.chenhe.qqnotifyevo.utils.PREFERENCE_ENABLE_LOG_DEFAULT
import cc.chenhe.qqnotifyevo.utils.dataStore
import cc.chenhe.qqnotifyevo.utils.getLogDir
import cc.chenhe.qqnotifyevo.utils.getNotificationChannels
import cc.chenhe.qqnotifyevo.utils.getVersion
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber


class MyApplication : Application() {
    companion object {
        private const val TAG = "Application"
    }

    private val logMutex = Mutex()
    private var enableLog: Boolean = PREFERENCE_ENABLE_LOG_DEFAULT

    private var debugTree: Timber.DebugTree? = null
    private var releaseTree: ReleaseTree? = null

    override fun onCreate() {
        super.onCreate()

        runBlocking {
            enableLog =
                dataStore.data.first()[PREFERENCE_ENABLE_LOG] ?: PREFERENCE_ENABLE_LOG_DEFAULT
            setupTimber(enableLog, false)
        }
        MainScope().launch {
            dataStore.data.map { it[PREFERENCE_ENABLE_LOG] ?: PREFERENCE_ENABLE_LOG_DEFAULT }
                .collectLatest {
                    if (enableLog != it) {
                        enableLog = it
                        setupTimber(it, false)
                    }
                }
        }

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)
        Timber.tag(TAG).i("\n\n")
        Timber.tag(TAG).i("==================================================")
        Timber.tag(TAG).i("= App Create  ver: ${getVersion(this)}")
        Timber.tag(TAG).i("==================================================\n")
        registerNotificationChannel()
    }

    private suspend fun setupTimber(enableLog: Boolean, deleteLog: Boolean) {
        logMutex.withLock {
            if (deleteLog) {
                releaseTree?.close()
                releaseTree = null
                getLogDir(this).deleteRecursively()
            }

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

    }

    private fun plantIfNotExist(tree: Timber.Tree) {
        if (!Timber.forest().contains(tree))
            Timber.plant(tree)
    }

    suspend fun deleteLog() {
        setupTimber(enableLog, true)
    }

    private fun registerNotificationChannel() {
        Timber.tag(TAG).d("Register system notification channels")
        val group =
            NotificationChannelGroup(NOTIFY_QQ_GROUP_ID, getString(R.string.notify_group_base))


        val att = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val tipChannel = NotificationChannel(
            NOTIFY_SELF_TIPS_CHANNEL_ID,
            getString(R.string.notify_self_tips_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
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
