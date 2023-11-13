package cc.chenhe.qqnotifyevo.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import cc.chenhe.qqnotifyevo.utils.NotifyChannel
import cc.chenhe.qqnotifyevo.utils.Tag
import cc.chenhe.qqnotifyevo.utils.hasPermission
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.util.*

/**
 * 配合 [cc.chenhe.qqnotifyevo.service.NotificationMonitorService] 使用的通知处理器，将直接发送新的通知并将原生通知移除。
 *
 * 部分版本的QQ当有多个联系人发来消息时，会合并为一个通知 「`有 x 个联系人给你发过来y条新消息`」，这种情况下 Nevo 模式无法正常工作，因为 Nevo
 * 只能对原始通知进行修改，无法把1个通知拆分成多个。[NotificationProcessor] 也仅会返回最新通知对应的会话，因此这个类进行了必要的修改，
 * 将会遍历所有历史会话并分别发送通知。
 */
class InnerNotificationProcessor(
    private val commander: Commander,
    context: Context,
    scope: CoroutineScope,
) : NotificationProcessor(context, scope) {

    companion object {
        private const val TAG = "InnerNotifyProcessor"
    }

    interface Commander {
        fun cancelNotification(key: String)
    }

    // 储存所有通知的 id 以便清除
    private val qqNotifyIds: MutableSet<Int> = HashSet()
    private val timNotifyIds: MutableSet<Int> = HashSet()

    /**
     * 清空对应来源的通知与历史记录，内部调用了单参 [clearHistory].
     */
    fun clearHistory(context: Context, tag: Tag) {
        clearHistory(tag)
        val ids = when (tag) {
            Tag.QQ -> qqNotifyIds
            Tag.TIM -> timNotifyIds
            Tag.UNKNOWN -> null
        }
        Timber.tag(TAG).v("Clear all evolutionary notifications.")
        NotificationManagerCompat.from(context).apply {
            ids?.forEach { id -> cancel(id) }
            ids?.clear()
        }
    }

    private fun sendNotification(context: Context, tag: Tag, id: Int, notification: Notification) {
        @SuppressLint("MissingPermission")
        if (context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            NotificationManagerCompat.from(context).notify(id, notification)
            addNotifyId(tag, id)
        }
    }

    override fun renewQzoneNotification(
        context: Context, tag: Tag, conversation: Conversation,
        sbn: StatusBarNotification, original: Notification
    ): Notification {

        val notification = createQZoneNotification(context, tag, conversation, original).apply {
            contentIntent = original.contentIntent
            deleteIntent = original.deleteIntent
        }
        sendNotification(context, tag, "QZone".hashCode(), notification)
        commander.cancelNotification(sbn.key)

        return notification
    }

    override fun renewConversionNotification(
        context: Context,
        tag: Tag,
        channel: NotifyChannel,
        conversation: Conversation,
        sbn: StatusBarNotification,
        original: Notification
    ): Notification {
        val history = getHistoryMessage(tag)
        var notification: Notification? = null
        for (c in history) {
            if (c.name != conversation.name || c.isGroup && channel != NotifyChannel.GROUP ||
                !c.isGroup && channel == NotifyChannel.GROUP
            ) {
                // 确保只刷新新增的通知
                continue
            }
            notification =
                createConversationNotification(context, tag, channel, c, original).apply {
                    contentIntent = original.contentIntent
                    deleteIntent = original.deleteIntent
                }
            sendNotification(context, tag, c.name.hashCode(), notification)
            commander.cancelNotification(sbn.key)
        }
        return notification ?: Notification() // 此处返回值没有实际意义
    }

    override fun buildNotification(
        builder: NotificationCompat.Builder,
        shortcutInfo: ShortcutInfoCompat?
    ): Notification {
        if (shortcutInfo != null) {
            ShortcutManagerCompat.pushDynamicShortcut(ctx, shortcutInfo)
            builder.setShortcutId(shortcutInfo.id)
        }
        return builder.build()
    }

    private fun addNotifyId(tag: Tag, ids: Int) {
        when (tag) {
            Tag.QQ -> qqNotifyIds.add(ids)
            Tag.TIM -> timNotifyIds.add(ids)
            Tag.UNKNOWN -> {
            }
        }
    }
}