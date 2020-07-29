package cc.chenhe.qqnotifyevo.core

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.StaticReceiver
import cc.chenhe.qqnotifyevo.preference.PreferenceAty
import cc.chenhe.qqnotifyevo.utils.*

/**
 * 配合 [cc.chenhe.qqnotifyevo.service.NevoDecorator] 使用的通知处理器，直接创建并返回优化后的通知。
 */
class NevoNotificationProcessor(context: Context) : NotificationProcessor(context) {

    companion object {
        private const val REQ_MULTI_MSG_LEARN_MORE = 1
        private const val REQ_MULTI_MSG_DONT_SHOW = 2
    }

    override fun renewQzoneNotification(context: Context, tag: Int, conversation: Conversation,
                                        sbn: StatusBarNotification, original: Notification): Notification {
        return createQZoneNotification(context, tag, conversation, original)
    }

    override fun renewConversionNotification(context: Context, tag: Int, channel: NotifyChannel,
                                             conversation: Conversation, sbn: StatusBarNotification,
                                             original: Notification): Notification {
        return createConversationNotification(context, tag, channel, conversation, original)
    }

    override fun onMultiMessageDetected() {
        super.onMultiMessageDetected()
        if (nevoMultiMsgTip(ctx)) {
            val dontShow = PendingIntent.getBroadcast(ctx, REQ_MULTI_MSG_DONT_SHOW,
                    Intent(ctx, StaticReceiver::class.java).also {
                        it.action = ACTION_MULTI_MSG_DONT_SHOW
                    }, PendingIntent.FLAG_UPDATE_CURRENT)

            val learnMore = PendingIntent.getActivity(ctx, REQ_MULTI_MSG_LEARN_MORE,
                    Intent(ctx, PreferenceAty::class.java).also {
                        it.putExtra(PreferenceAty.EXTRA_NEVO_MULTI_MSG, true)
                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }, PendingIntent.FLAG_UPDATE_CURRENT)

            val style = NotificationCompat.BigTextStyle()
                    .setBigContentTitle(ctx.getString(R.string.notify_multi_msg_title))
                    .bigText(ctx.getString(R.string.notify_multi_msg_content))
            val n = NotificationCompat.Builder(ctx, NOTIFY_SELF_TIPS_CHANNEL_ID)
                    .setStyle(style)
                    .setAutoCancel(true)
                    .setContentTitle(ctx.getString(R.string.notify_multi_msg_title))
                    .setContentText(ctx.getString(R.string.notify_multi_msg_content))
                    .setSmallIcon(R.drawable.ic_notify_warning)
                    .setContentIntent(learnMore)
                    .addAction(R.drawable.ic_notify_action_dnot_show, ctx.getString(R.string.dont_show), dontShow)
                    .addAction(R.drawable.ic_notify_action_learn_more, ctx.getString(R.string.learn_more), learnMore)
                    .build()

            NotificationManagerCompat.from(ctx).notify(NOTIFY_ID_MULTI_MSG, n)
        }
    }

}