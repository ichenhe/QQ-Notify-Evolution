package cc.chenhe.qqnotifyevo.core

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.IntDef
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.service.NotificationMonitorService
import cc.chenhe.qqnotifyevo.utils.*
import java.util.*
import java.util.regex.Pattern

class NotificationProcessor {

    companion object {
        private const val TAG = "NotificationProcessor"

        private const val CONVERSATION_NAME_QZONE = "QZone"

        /**
         * QQ 隐藏通知详情时代理的通知 ID 偏移量，与 `TAG` 相加。
         */
        const val HIDE_MSG_NOTIFY_ID_OFFSET = 10

        @Retention(AnnotationRetention.SOURCE)
        @IntDef(TAG_UNKNOWN, TAG_QQ, TAG_QQ_LITE, TAG_TIM)
        annotation class SourceTag

        private const val TAG_UNKNOWN = 0
        private const val TAG_QQ = 1
        private const val TAG_QQ_LITE = 2
        private const val TAG_TIM = 3

        @SourceTag
        private fun getTagFromPackageName(packageName: String): Int {
            return when (packageName) {
                "com.tencent.mobileqq" -> TAG_QQ
                "com.tencent.tim" -> TAG_TIM
                "com.tencent.qqlite" -> TAG_QQ_LITE
                else -> TAG_UNKNOWN
            }
        }

        // 群聊消息
        // title: 群名 | 群名 (x条新消息)
        // ticker: 昵称(群名):消息内容
        // text: 昵称: 消息内容
        @VisibleForTesting
        val groupMsgPattern: Pattern = Pattern.compile("^(.+)\\((.*)\\):(.+)$")

        // 私聊消息
        // title: 昵称 | 昵称 (x条新消息) //特别关心前缀：[特别关心]
        // ticker: 昵称: 消息内容
        // text: 消息内容
        @VisibleForTesting
        val msgPattern: Pattern = Pattern.compile("^(.+?): (.+)$")

        // Q空间动态
        // title: QQ空间动态(共x条未读)
        // ticker: 详情（例如xxx评论了你）
        // text: 与 ticker 相同
        @VisibleForTesting
        val qzonePattern: Pattern = Pattern.compile("^QQ空间动态\\(共(\\d+)条未读\\)$")

        private val hideMsgPattern = Pattern.compile("^你收到了(\\d+)条新消息$")

        private val qqHistory = ArrayList<Conversation>()
        private val qqLiteHistory = ArrayList<Conversation>()
        private val timHistory = ArrayList<Conversation>()
        private val qzoneHistory = ArrayList<Message>()
    }


    /**
     * @param packageName 来源应用包名。
     * @param sbn 原始通知。
     */
    fun resolveNotification(context: Context, packageName: String, sbn: StatusBarNotification): Notification? {
        val original = sbn.notification ?: return null
        val tag = getTagFromPackageName(packageName)
        if (tag == TAG_UNKNOWN)
            return null

        val title = original.extras.getString(Notification.EXTRA_TITLE)
        val content = original.extras.getString(Notification.EXTRA_TEXT)
        val ticker = original.tickerText?.toString()

        // 单独处理QQ空间
        val isQzone = title?.let { qzonePattern.matcher(it).matches() } ?: false

        Log.v(TAG, "标题: $title; Ticker: $ticker; Q空间: $isQzone; 内容: $content")


        // 隐藏消息详情
        // title: QQ
        // ticker = text
        // text 你收到了x条新消息
        if (ticker != null && ticker == content && hideMsgPattern.matcher(ticker).matches()) {
            return createNotification(context, tag, NotifyChannel.FRIEND, null, null, original, title, content, ticker)
        }

        // QQ空间
        if (isQzone && !content.isNullOrEmpty()) {
            saveConversationIcon(context, CONVERSATION_NAME_QZONE.hashCode(), getNotifyLargeIcon(context, original))
            qzoneHistory.add(Message(context.getString(R.string.notify_qzone_channel_name),
                    getConversionIcon(context, CONVERSATION_NAME_QZONE.hashCode()), content))
            Log.d(TAG, "[QZone] Ticker: $ticker")
            return createQZoneNotification(context, tag, original)
        }

        if (ticker == null)
            return null

        // 群消息
        groupMsgPattern.matcher(ticker).also { matcher ->
            if (matcher.matches()) {
                val name = matcher.group(1) ?: return null
                val groupName = matcher.group(2) ?: return null
                val text = matcher.group(3) ?: return null

                saveConversationIcon(context, groupName.hashCode(), getNotifyLargeIcon(context, original))
                val conversation = addMessage(tag, name, text, groupName,
                        getConversionIcon(context, groupName.hashCode()), original.contentIntent, original.deleteIntent)
                Log.d(TAG, "[Group] Name: $name; Group: $groupName; Text: $text")
                return createConversionNotification(context, tag, NotifyChannel.GROUP, conversation, original)
            }
        }

        // 私聊消息
        msgPattern.matcher(ticker).also { matcher ->
            if (matcher.matches()) {
                val name = matcher.group(1) ?: return null
                val text = matcher.group(2) ?: return null

                saveConversationIcon(context, name.hashCode(), getNotifyLargeIcon(context, original))
                val conversation = addMessage(tag, name, text, null, getConversionIcon(context, name.hashCode()),
                        original.contentIntent, original.deleteIntent)
                Log.d(TAG, "[Friend] Name: $name; Text: $text")
                return createConversionNotification(context, tag, NotifyChannel.FRIEND, conversation, original)
            }
        }
        Log.w(TAG, "[None] Not match any pattern.")
        return null
    }


    /**
     * 获取通知的大图标。
     *
     * @param notification 原有通知。
     * @return 通知的大图标。
     */
    private fun getNotifyLargeIcon(context: Context, notification: Notification): Bitmap {
        return notification.getLargeIcon().loadDrawable(context).toBitmap()
    }

    /**
     * 创建新样式的通知。
     *
     * @param tag       来源标记。
     * @param channel   通知渠道。
     * @param style     通知样式。
     * @param largeIcon 大图标。
     * @param original  原始通知。
     */
    private fun createNotification(
            context: Context,
            @SourceTag tag: Int,
            channel: NotifyChannel,
            style: NotificationCompat.Style?,
            largeIcon: Bitmap?,
            original: Notification,
            title: String? = null, text: String? = null, ticker: String? = null): Notification {
        val channelId = getChannelId(channel)

        val color = ContextCompat.getColor(context,
                if (channel == NotifyChannel.QZONE) R.color.colorQzone else R.color.colorPrimary)

        @Suppress("DEPRECATION")
        val builder = NotificationCompat.Builder(context, channelId)
                .setColor(color)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setStyle(style)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setLights(original.ledARGB, original.ledOnMS, original.ledOffMS)
                .setLargeIcon(largeIcon)
                .setChannelId(channelId)

        if (title != null)
            builder.setContentTitle(title)
        if (text != null)
            builder.setContentText(text)
        if (ticker != null)
            builder.setTicker(ticker)

        setIcon(context, builder, tag, channel == NotifyChannel.QZONE)

        return builder.build()
    }

    private fun createQZoneNotification(context: Context, @SourceTag tag: Int, original: Notification): Notification {
        val style = NotificationCompat.MessagingStyle(Person.Builder()
                .setName(context.getString(R.string.notify_qzone_channel_name)).build())
        qzoneHistory.forEach { msg ->
            style.addMessage(msg.content, msg.time, msg.person)
        }
        return createNotification(context, tag, NotifyChannel.QZONE, style,
                getConversionIcon(context, CONVERSATION_NAME_QZONE.hashCode()), original)
    }


    /**
     * 创建会话消息通知。
     *
     * @param tag      来源标记。
     * @param original 原始通知。
     */
    private fun createConversionNotification(context: Context, @SourceTag tag: Int, channel: NotifyChannel,
                                             conversation: Conversation, original: Notification): Notification {
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(conversation.name).build())
        if (conversation.isGroup) {
            style.conversationTitle = conversation.name
            style.isGroupConversation = true
        }
        conversation.messages.forEach { msg ->
            style.addMessage(msg.content, msg.time, msg.person)
        }
        return createNotification(context, tag, channel, style, getConversionIcon(context, conversation.name.hashCode()), original)
    }


    private fun setIcon(context: Context, builder: NotificationCompat.Builder, tag: Int, isQzone: Boolean) {
        if (isQzone) {
            builder.setSmallIcon(R.drawable.ic_qzone)
            return
        }
        val mode = Integer.parseInt(getIconMode(context))
        when (mode) {
            0 -> when (tag) {
                NotificationMonitorService.TAG_QQ, NotificationMonitorService.TAG_QQ_LITE -> R.drawable.ic_qq
                NotificationMonitorService.TAG_TIM -> R.drawable.ic_tim
                else -> R.drawable.ic_qq
            }
            1 -> R.drawable.ic_tim
            2 -> R.drawable.chat2
            3 -> R.drawable.chat
            else -> R.drawable.ic_qq
        }.let { iconRes -> builder.setSmallIcon(iconRes) }
    }


    /**
     * 获取历史消息。
     */
    private fun getHistoryMessage(@SourceTag tag: Int): ArrayList<Conversation> {
        return when (tag) {
            TAG_TIM -> timHistory
            TAG_QQ_LITE -> qqLiteHistory
            else -> qqHistory
        }
    }

    /**
     * 加入历史消息记录。
     */
    private fun addMessage(@SourceTag tag: Int, name: String, content: String, group: String?, icon: Bitmap?,
                           contentIntent: PendingIntent, deleteIntent: PendingIntent): Conversation {
        var conversation: Conversation? = null
        // 以会话名为标准寻找已存在的会话
        for (item in getHistoryMessage(tag)) {
            if (group != null) {
                if (item.isGroup && item.name == group) {
                    conversation = item
                    break
                }
            } else {
                if (!item.isGroup && item.name == name) {
                    conversation = item
                    break
                }
            }
        }
        if (conversation == null) {
            // 创建新会话
            conversation = Conversation(group != null, group ?: name, contentIntent, deleteIntent)
            getHistoryMessage(tag).add(conversation)
        }
        conversation.messages.add(Message(name, icon, content))
        return conversation
    }


    private data class Conversation(
            val isGroup: Boolean,
            val name: String,
            var contentIntent: PendingIntent,
            var deleteIntent: PendingIntent) {
        val messages = ArrayList<Message>()
    }

    /**
     * @param name 发送者昵称。
     * @param icon 头像。
     * @param content 消息内容。
     */
    private data class Message(val name: String, val icon: Bitmap?, val content: String) {
        val person: Person = Person.Builder()
                .setIcon(icon?.let { IconCompat.createWithBitmap(it) })
                .setName(name)
                .build()
        val time = System.currentTimeMillis()
    }
}