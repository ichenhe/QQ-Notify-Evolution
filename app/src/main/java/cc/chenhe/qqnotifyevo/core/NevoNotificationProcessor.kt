package cc.chenhe.qqnotifyevo.core

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import cc.chenhe.qqnotifyevo.utils.NotifyChannel

/**
 * 配合 [cc.chenhe.qqnotifyevo.service.NevoDecorator] 使用的通知处理器，直接创建并返回优化后的通知。
 */
class NevoNotificationProcessor : NotificationProcessor() {

    override fun renewQzoneNotification(context: Context, tag: Int, conversation: Conversation,
                                        sbn: StatusBarNotification, original: Notification): Notification {
        return createQZoneNotification(context, tag, conversation, original)
    }

    override fun renewConversionNotification(context: Context, tag: Int, channel: NotifyChannel,
                                             conversation: Conversation, sbn: StatusBarNotification,
                                             original: Notification): Notification {
        return createConversionNotification(context, tag, channel, conversation, original)
    }

}