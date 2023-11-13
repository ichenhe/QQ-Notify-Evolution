package cc.chenhe.qqnotifyevo.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.StaticReceiver
import cc.chenhe.qqnotifyevo.ui.MainActivity
import cc.chenhe.qqnotifyevo.utils.*
import kotlinx.coroutines.CoroutineScope

/**
 * 配合 [cc.chenhe.qqnotifyevo.service.NevoDecorator] 使用的通知处理器，直接创建并返回优化后的通知。
 */
class NevoNotificationProcessor(context: Context, scope: CoroutineScope) :
    NotificationProcessor(context, scope) {

    companion object {
        private const val REQ_MULTI_MSG_LEARN_MORE = 1
        private const val REQ_MULTI_MSG_DONT_SHOW = 2
    }

    override fun renewQzoneNotification(
        context: Context,
        tag: Tag,
        conversation: Conversation,
        sbn: StatusBarNotification,
        original: Notification
    ): Notification {
        return createQZoneNotification(context, tag, conversation, original)
    }

    override fun renewConversionNotification(
        context: Context,
        tag: Tag,
        channel: NotifyChannel,
        conversation: Conversation,
        sbn: StatusBarNotification,
        original: Notification
    ): Notification {
        return createConversationNotification(context, tag, channel, conversation, original)
    }

    override fun onMultiMessageDetected(isBindingMsg: Boolean) {
        super.onMultiMessageDetected(isBindingMsg)
        if (isBindingMsg) {
            // 目前关联账号的消息都会合并
            return
        }
        @SuppressLint("MissingPermission")
        if (nevoMultiMsgTip(ctx) && ctx.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            val dontShow = PendingIntent.getBroadcast(
                ctx, REQ_MULTI_MSG_DONT_SHOW,
                Intent(ctx, StaticReceiver::class.java).also {
                    it.action = ACTION_MULTI_MSG_DONT_SHOW
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val learnMore = PendingIntent.getActivity(
                ctx, REQ_MULTI_MSG_LEARN_MORE,
                Intent(ctx, MainActivity::class.java).also {
                    it.putExtra(MainActivity.EXTRA_NEVO_MULTI_MSG, true)
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

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
                .addAction(
                    R.drawable.ic_notify_action_dnot_show,
                    ctx.getString(R.string.dont_show),
                    dontShow
                )
                .addAction(
                    R.drawable.ic_notify_action_learn_more,
                    ctx.getString(R.string.learn_more),
                    learnMore
                )
                .build()

            NotificationManagerCompat.from(ctx).notify(NOTIFY_ID_MULTI_MSG, n)
        }
    }

}