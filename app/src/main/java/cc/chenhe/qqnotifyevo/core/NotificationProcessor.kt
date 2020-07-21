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
import cc.chenhe.qqnotifyevo.utils.*
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

abstract class NotificationProcessor {

    companion object {
        private const val TAG = "NotificationProcessor"

        private const val CONVERSATION_NAME_QZONE = "QZone"

        @Retention(AnnotationRetention.SOURCE)
        @IntDef(TAG_UNKNOWN, TAG_QQ, TAG_QQ_LITE, TAG_TIM)
        annotation class SourceTag

        const val TAG_UNKNOWN = 0
        const val TAG_QQ = 1
        const val TAG_QQ_LITE = 2
        const val TAG_TIM = 3

        @SourceTag
        fun getTagFromPackageName(packageName: String): Int {
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

        /**
         * 匹配群聊消息 Ticker.
         *
         * Group: 1昵称, 2群名, 3消息内容
         *
         * 限制：昵称不能包含英文括号 `()`.
         */
        @VisibleForTesting
        val groupMsgPattern: Pattern = Pattern.compile("^(.+?)\\((.+)\\):(.+)$")

        // 私聊消息
        // title: 昵称 | 昵称 (x条新消息) //特别关心前缀：[特别关心]
        // ticker: 昵称: 消息内容
        // text: 消息内容

        /**
         * 匹配私聊消息 Ticker.
         *
         * Group: 1昵称, 2消息内容
         */
        @VisibleForTesting
        val msgPattern: Pattern = Pattern.compile("^(.+?): (.+)$")

        /**
         * 匹配私聊消息 Title.
         *
         * Group: 1\[特别关心\], 2新消息数目
         */
        @VisibleForTesting
        val msgTitlePattern: Pattern = Pattern.compile("^(\\[特别关心])?.*?(?: \\((\\d+)条新消息\\))?$")

        // Q空间动态
        // title: QQ空间动态(共x条未读)
        // ticker: 详情（例如xxx评论了你）
        // text: 与 ticker 相同

        /**
         * 匹配 QQ 空间 Ticker.
         *
         * Group: 1新消息数目
         */
        @VisibleForTesting
        val qzonePattern: Pattern = Pattern.compile("^QQ空间动态\\(共(\\d+)条未读\\)$")

        // 隐藏消息详情
        // title: QQ
        // ticker: 你收到了x条新消息
        // text: 与 ticker 相同

        /**
         * 匹配隐藏通知详情时的 Ticker.
         *
         * Group: 1新消息数目
         */
        @VisibleForTesting
        val hideMsgPattern: Pattern = Pattern.compile("^你收到了(\\d+)条新消息$")

    }

    private val qqHistory = ArrayList<Conversation>()
    private val qqLiteHistory = ArrayList<Conversation>()
    private val timHistory = ArrayList<Conversation>()

    /**
     * 清除此来源所有通知，并清空历史记录。
     *
     * @param tag 来源标记。
     */
    fun clearHistory(@SourceTag tag: Int) {
        when (tag) {
            TAG_QQ -> {
                qqHistory.clear()
            }
            TAG_QQ_LITE -> {
                qqLiteHistory.clear()
            }
            TAG_TIM -> {
                timHistory.clear()
            }
        }
    }

    /**
     * 创建优化后的QQ空间通知。
     *
     * @param tag 来源应用标记。
     * @param conversation 需要展示的内容。
     * @param original 原始通知。
     *
     * @return 优化后的通知。
     */
    protected abstract fun renewQzoneNotification(
            context: Context,
            @SourceTag tag: Int,
            conversation: Conversation,
            sbn: StatusBarNotification,
            original: Notification
    ): Notification

    /**
     * 创建优化后的会话消息通知。
     *
     * @param tag 来源应用标记。
     * @param channel 隶属的通知渠道。
     * @param conversation 需要展示的内容。
     * @param original 原始通知。
     *
     * @return 优化后的通知。
     */
    protected abstract fun renewConversionNotification(
            context: Context,
            @SourceTag tag: Int,
            channel: NotifyChannel,
            conversation: Conversation,
            sbn: StatusBarNotification,
            original: Notification
    ): Notification


    /**
     * 解析原始通知，返回优化后的通知。
     *
     * @param packageName 来源应用包名。
     * @param sbn 原始通知。
     * @return 优化后的通知。若未匹配到已知模式或消息内容被隐藏则返回 `null`.
     */
    fun resolveNotification(context: Context, packageName: String, sbn: StatusBarNotification): Notification? {
        val original = sbn.notification ?: return null
        val tag = getTagFromPackageName(packageName)
        if (tag == TAG_UNKNOWN)
            return null

        val title = original.extras.getString(Notification.EXTRA_TITLE)
        val content = original.extras.getString(Notification.EXTRA_TEXT)
        val ticker = original.tickerText?.toString()

        // 多个消息
        // title: QQ
        // ticker: 昵称:内容
        // text: 有 x 个联系人给你发过来y条新消息
        val isMulti = content?.let {
            !it.contains(":") && !(ticker?.endsWith(it) ?: false)
        } ?: false

        // 单独处理QQ空间
        val isQzone = title?.let { qzonePattern.matcher(it).matches() } ?: false

        Log.v(TAG, "标题: $title; Ticker: $ticker; Q空间: $isQzone; 内容: $content")


        // 隐藏消息详情
        if (ticker != null && ticker == content && hideMsgPattern.matcher(ticker).matches()) {
            return null
        }

        // QQ空间
        if (isQzone && !content.isNullOrEmpty()) {
            saveConversationIcon(context, CONVERSATION_NAME_QZONE.hashCode(), getNotifyLargeIcon(context, original))

            val conversation = addMessage(tag, context.getString(R.string.notify_qzone_channel_name), content, null,
                    getConversionIcon(context, CONVERSATION_NAME_QZONE.hashCode()), original.contentIntent,
                    original.deleteIntent)
            deleteOldMessage(conversation, matchQzoneNum(title))
            Log.d(TAG, "[QZone] Ticker: $ticker")
            return renewQzoneNotification(context, tag, conversation, sbn, original)
        }

        if (ticker == null)
            return null

        // 群消息
        groupMsgPattern.matcher(ticker).also { matcher ->
            if (matcher.matches()) {
                val name = matcher.group(1) ?: return null
                val groupName = matcher.group(2) ?: return null
                val text = matcher.group(3) ?: return null
                if (!isMulti)
                    saveConversationIcon(context, groupName.hashCode(), getNotifyLargeIcon(context, original))
                val conversation = addMessage(tag, name, text, groupName,
                        getConversionIcon(context, groupName.hashCode()), original.contentIntent, original.deleteIntent)
                deleteOldMessage(conversation, if (isMulti) 0 else matchMessageNum(title))
                Log.d(TAG, "[Group] Name: $name; Group: $groupName; Text: $text")
                return renewConversionNotification(context, tag, NotifyChannel.GROUP, conversation, sbn, original)
            }
        }

        // 私聊消息
        msgPattern.matcher(ticker).also { matcher ->
            if (matcher.matches()) {
                val titleMatcher = msgTitlePattern.matcher(title ?: "")
                val special = titleMatcher.matches() && titleMatcher.group(1) != null
                val name = matcher.group(1) ?: return null
                val text = matcher.group(2) ?: return null
                if (!isMulti)
                    saveConversationIcon(context, name.hashCode(), getNotifyLargeIcon(context, original))
                val conversation = addMessage(tag, name, text, null, getConversionIcon(context, name.hashCode()),
                        original.contentIntent, original.deleteIntent)
                deleteOldMessage(conversation, if (isMulti) 0 else matchMessageNum(titleMatcher))
                return if (special) {
                    Log.d(TAG, "[Special] Name: $name; Text: $text")
                    renewConversionNotification(context, tag, NotifyChannel.FRIEND_SPECIAL, conversation, sbn, original)
                } else {
                    Log.d(TAG, "[Friend] Name: $name; Text: $text")
                    renewConversionNotification(context, tag, NotifyChannel.FRIEND, conversation, sbn, original)
                }
            }
        }
        Log.w(TAG, "[None] Not match any pattern.")
        return null
    }

    /**
     * 提取新消息个数。
     */
    private fun matchMessageNum(text: String?): Int {
        if (text.isNullOrEmpty()) return 0
        return matchMessageNum(msgTitlePattern.matcher(text))
    }

    /**
     * @param matcher [msgTitlePattern] 生成的匹配器。
     */
    private fun matchMessageNum(matcher: Matcher): Int {
        if (matcher.matches()) {
            return matcher.group(2)?.toInt() ?: 1
        }
        return 1
    }

    /**
     * 提取空间未读消息个数。
     */
    private fun matchQzoneNum(title: String?): Int {
        if (title.isNullOrEmpty()) return 0
        qzonePattern.matcher(title).also { matcher ->
            if (matcher.matches()) {
                return matcher.group(1)!!.toInt()
            }
        }
        return 1
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
            subtext: String? = null,
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

        if (subtext != null)
            builder.setSubText(subtext)
        if (title != null)
            builder.setContentTitle(title)
        if (text != null)
            builder.setContentText(text)
        if (ticker != null)
            builder.setTicker(ticker)

        setIcon(context, builder, tag, channel == NotifyChannel.QZONE)

        return builder.build()
    }

    protected fun createQZoneNotification(context: Context, @SourceTag tag: Int, conversation: Conversation,
                                          original: Notification): Notification {
        val style = NotificationCompat.MessagingStyle(Person.Builder()
                .setName(context.getString(R.string.notify_qzone_channel_name)).build())
        conversation.messages.forEach { msg ->
            style.addMessage(msg.content, msg.time, msg.person)
        }
        val num = conversation.messages.size
        val subtext = if (num > 1) context.getString(R.string.notify_subtext_qzone_num, num) else null
        return createNotification(context, tag, NotifyChannel.QZONE, style,
                getConversionIcon(context, CONVERSATION_NAME_QZONE.hashCode()), original, subtext)
    }


    /**
     * 创建会话消息通知。
     *
     * @param tag      来源标记。
     * @param original 原始通知。
     */
    protected fun createConversionNotification(context: Context, @SourceTag tag: Int, channel: NotifyChannel,
                                               conversation: Conversation, original: Notification): Notification {
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(conversation.name).build())
        if (conversation.isGroup) {
            style.conversationTitle = conversation.name
            style.isGroupConversation = true
        }
        conversation.messages.forEach { msg ->
            style.addMessage(msg.content, msg.time, msg.person)
        }
        val num = conversation.messages.size
        val subtext = if (num > 1) context.getString(R.string.notify_subtext_message_num, num) else null
        return createNotification(context, tag, channel, style,
                getConversionIcon(context, conversation.name.hashCode()), original, subtext)
    }


    private fun setIcon(context: Context, builder: NotificationCompat.Builder, tag: Int, isQzone: Boolean) {
        if (isQzone) {
            builder.setSmallIcon(R.drawable.ic_qzone)
            return
        }
        when (getIconMode(context)) {
            ICON_AUTO -> when (tag) {
                TAG_QQ, TAG_QQ_LITE -> R.drawable.ic_qq
                TAG_TIM -> R.drawable.ic_tim
                else -> R.drawable.ic_qq
            }
            ICON_QQ -> R.drawable.ic_qq
            ICON_TIM -> R.drawable.ic_tim
            else -> R.drawable.ic_qq
        }.let { iconRes -> builder.setSmallIcon(iconRes) }
    }


    /**
     * 获取历史消息。
     */
    protected fun getHistoryMessage(@SourceTag tag: Int): ArrayList<Conversation> {
        return when (tag) {
            TAG_TIM -> timHistory
            TAG_QQ_LITE -> qqLiteHistory
            TAG_QQ -> qqHistory
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

    /**
     * 删除旧的消息，直到剩余消息个数 <= [maxMessageNum].
     *
     * @param conversation 要清理消息的会话。
     * @param maxMessageNum 最多允许的消息个数，若小于1则忽略。
     */
    private fun deleteOldMessage(conversation: Conversation, maxMessageNum: Int) {
        if (maxMessageNum < 1)
            return
        if (conversation.messages.size <= maxMessageNum)
            return
        while (conversation.messages.size > maxMessageNum) {
            conversation.messages.removeAt(0)
        }
    }

    protected data class Conversation(
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
    protected data class Message(val name: String, val icon: Bitmap?, val content: String) {
        val person: Person = Person.Builder()
                .setIcon(icon?.let { IconCompat.createWithBitmap(it) })
                .setName(name)
                .build()
        val time = System.currentTimeMillis()
    }
}