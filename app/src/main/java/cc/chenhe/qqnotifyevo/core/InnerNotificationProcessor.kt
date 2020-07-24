package cc.chenhe.qqnotifyevo.core

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import cc.chenhe.qqnotifyevo.utils.NotifyChannel
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
        context: Context
) : NotificationProcessor(context) {

    companion object {
        private const val TAG = "InnerNotifyProcessor"
    }

    interface Commander {
        fun cancelNotification(key: String)
    }

    // 储存所有通知的 id 以便清除
    private val qqNotifyIds: MutableSet<Int> = HashSet()
    private val qqLiteNotifyIds: MutableSet<Int> = HashSet()
    private val timNotifyIds: MutableSet<Int> = HashSet()

    /**
     * 清空对应来源的通知与历史记录，内部调用了单参 [clearHistory].
     */
    fun clearHistory(context: Context, tag: Int) {
        clearHistory(tag)
        val ids = when (tag) {
            TAG_QQ -> {
                qqNotifyIds
            }
            TAG_QQ_LITE -> {
                qqLiteNotifyIds
            }
            TAG_TIM -> {
                timNotifyIds
            }
            else -> null
        }
        Timber.tag(TAG).v("Clear all evolutionary notifications.")
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).run {
            ids?.forEach { id -> cancel(id) }
        }
    }

    private fun sendNotification(context: Context, @NotificationProcessor.Companion.SourceTag tag: Int, id: Int,
                                 notification: Notification) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, notification)
        addNotifyId(tag, id)
    }

    override fun renewQzoneNotification(context: Context, tag: Int, conversation: Conversation,
                                        sbn: StatusBarNotification, original: Notification): Notification {

        val notification = createQZoneNotification(context, tag, conversation, original).apply {
            contentIntent = original.contentIntent
            deleteIntent = original.deleteIntent
        }
        sendNotification(context, tag, "QZone".hashCode(), notification)
        commander.cancelNotification(sbn.key)

        return notification
    }

    override fun renewConversionNotification(context: Context, tag: Int, channel: NotifyChannel,
                                             conversation: Conversation, sbn: StatusBarNotification,
                                             original: Notification): Notification {
        val history = getHistoryMessage(tag)
        var notification: Notification = createConversationNotification(context, tag, channel, conversation, original)
        for (c in history) {
            if (c.name != conversation.name || c.isGroup && channel != NotifyChannel.GROUP ||
                    !c.isGroup && channel == NotifyChannel.GROUP) {
                // 确保只刷新新增的通知
                continue
            }
            notification = createConversationNotification(context, tag, channel, c, original).apply {
                contentIntent = original.contentIntent
                deleteIntent = original.deleteIntent
            }
            sendNotification(context, tag, c.name.hashCode(), notification)
            commander.cancelNotification(sbn.key)
        }
        return notification
    }

    private fun addNotifyId(@NotificationProcessor.Companion.SourceTag tag: Int, ids: Int) {
        when (tag) {
            TAG_QQ -> qqNotifyIds.add(ids)
            TAG_TIM -> timNotifyIds.add(ids)
            TAG_QQ_LITE -> qqLiteNotifyIds.add(ids)
        }
    }
}