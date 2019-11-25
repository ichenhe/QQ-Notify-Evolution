package cc.chenhe.qqnotifyevo

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import cc.chenhe.qqnotifyevo.utils.*
import java.util.*
import java.util.regex.Pattern

class NotificationMonitorService : NotificationListenerService() {

    companion object {
        const val TAG = "NotifyMonitor"

        const val CONVERSATION_NAME_QZONE = "QZone"

        const val TAG_QQ = 1
        const val TAG_QQ_LITE = 2
        const val TAG_TIM = 3
        const val TAG_QZONE = 4

        /**
         * QQ 隐藏通知详情时代理的通知 ID 偏移量，与 `TAG` 相加。
         */
        const val HIDE_MSG_NOTIFY_ID_OFFSET = 10

        fun getTagFromPackageName(packageName: String): Int {
            when (packageName) {
                "com.tencent.mobileqq" -> return TAG_QQ
                "com.tencent.tim" -> return TAG_TIM
                "com.tencent.qqlite" -> return TAG_QQ_LITE
            }
            return 0
        }
    }

    private val groupMsgPattern = Pattern.compile("^(.+)\\((.*)\\):(.+)$")
    private val msgPattern = Pattern.compile("^(.+):(.+)$")
    private val qzonePattern = Pattern.compile("^QQ空间动态\\(共(\\d+)条未读\\)$")
    private val hideMsgPattern = Pattern.compile("^你收到了(\\d+)条新消息$")

    private val qqHistory = ArrayList<Conversation>()
    private val qqLiteHistory = ArrayList<Conversation>()
    private val timHistory = ArrayList<Conversation>()
    private val qzoneHistory = ArrayList<Message>()

    // 储存所有通知的 id 以便清除
    private val qqNotifyIds: MutableSet<Int> = HashSet()
    private val qqLiteNotifyIds: MutableSet<Int> = HashSet()
    private val timNotifyIds: MutableSet<Int> = HashSet()
    private val qzoneNotifyIds: MutableSet<Int> = HashSet()
    private var qzoneContentIntent: PendingIntent? = null
    private var qzoneDeleteIntent: PendingIntent? = null

    private lateinit var ctx: Context

    override fun onCreate() {
        super.onCreate()
        ctx = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        getTagFromPackageName(sbn.packageName).let { tag ->
            if (tag != 0)
                resolveNotification(sbn, tag)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra("tag") == true) {
            clearNotification(intent.getIntExtra("tag", 0))
        }
        return Service.START_STICKY
    }

    /**
     * 解析通知。
     *
     * @param sbn 原始通知。
     * @param tag 来源标记。
     */
    private fun resolveNotification(sbn: StatusBarNotification, tag: Int) {
        val original = sbn.notification ?: return
        // 标题/内容
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

        Log.d(TAG, "标题: $title; Ticker: $ticker; 多个消息: $isMulti; Q空间: $isQzone; 内容: $content")


        // 隐藏消息详情
        // title: QQ
        // ticker = text
        // text 你收到了x条新消息
        if (ticker != null && ticker == content) {
            hideMsgPattern.matcher(ticker).let { matcher ->
                if (matcher.matches()) {
                    sendNotification(tag, NotifyChannel.FRIEND, null, tag + HIDE_MSG_NOTIFY_ID_OFFSET,
                            null, original, original.contentIntent, original.deleteIntent, title,
                            content, ticker)
                    cancelNotification(sbn.key)
                    return
                }
            }
        }

        if (isQzone && isQzoneNotify(this) && !content.isNullOrEmpty()) {
            saveConversationIcon(this, CONVERSATION_NAME_QZONE.hashCode(), getNotifyLargeIcon(original))
            qzoneHistory.add(Message(getString(R.string.notify_qzone_channel_name),
                    getConversionIcon(this, CONVERSATION_NAME_QZONE.hashCode()), content))
            qzoneContentIntent = original.contentIntent
            qzoneDeleteIntent = original.deleteIntent
            notifyQZoneMessage(original)
            cancelNotification(sbn.key)
            Log.d(TAG, "[QZone] ticker: $ticker")
            return
        }

        if (ticker == null)
            return

        groupMsgPattern.matcher(ticker).let { matcher ->
            if (matcher.matches() && isGroupNotify(this)) {
                val name = matcher.group(1) ?: return
                val groupName = matcher.group(2) ?: return
                val text = matcher.group(3) ?: return
                if (!isMulti) {
                    saveConversationIcon(this, groupName.hashCode(), getNotifyLargeIcon(original))
                }
                addMessage(tag, name, text, groupName,
                        getConversionIcon(this, groupName.hashCode()),
                        original.contentIntent, original.deleteIntent)
                        .name.let { conversationName ->
                    notifyConversionMessage(tag, NotifyChannel.GROUP, conversationName, original)
                }
                cancelNotification(sbn.key)
                Log.d(TAG, "[Group] Name: $name; Group: $groupName; text: $text")
                return
            }
        }

        msgPattern.matcher(ticker).let { matcher ->
            if (matcher.matches()) {
                val name = matcher.group(1) ?: return
                val text = matcher.group(2) ?: return
                if (!isMulti) {
                    saveConversationIcon(this, name.hashCode(), getNotifyLargeIcon(original))
                }
                addMessage(tag, name, text, null,
                        getConversionIcon(this, name.hashCode()),
                        original.contentIntent, original.deleteIntent)
                        .name.let { conversationName ->
                    notifyConversionMessage(tag, NotifyChannel.FRIEND, conversationName, original)
                }
                cancelNotification(sbn.key)
                Log.d(TAG, "[Friend] Name: $name; text: $text")
                return
            }
        }
        Log.w(TAG, "[None] Not match any pattern")
    }

    /**
     * 发送会话消息通知。
     *
     * @param tag      来源标记。
     * @param original 原始通知。
     */
    private fun notifyConversionMessage(tag: Int, channel: NotifyChannel, name: String, original: Notification) {
        val history = getHistoryMessage(tag)
        for (c in history) {
            if (c.name != name || c.isGroup && channel != NotifyChannel.GROUP
                    || !c.isGroup && channel == NotifyChannel.GROUP)
            // 确保只刷新新增的通知
                continue

            val style = NotificationCompat.MessagingStyle(Person.Builder().setName(c.name).build())
            if (c.isGroup) {
                style.conversationTitle = c.name
                style.isGroupConversation = true
            }
            c.messages.forEach { msg ->
                style.addMessage(msg.content, msg.time, msg.person)
            }
            sendNotification(tag, channel, style, c.name.hashCode(),
                    getConversionIcon(this, c.name.hashCode()), original, c.contentIntent, c.deleteIntent)
        }
    }

    private fun notifyQZoneMessage(original: Notification) {
        val style = NotificationCompat.MessagingStyle(Person.Builder()
                .setName(getString(R.string.notify_qzone_channel_name)).build())
        qzoneHistory.forEach { msg ->
            style.addMessage(msg.content, msg.time, msg.person)
        }
        sendNotification(TAG_QZONE, NotifyChannel.QZONE, style, CONVERSATION_NAME_QZONE.hashCode(),
                getConversionIcon(ctx, CONVERSATION_NAME_QZONE.hashCode()), original,
                qzoneContentIntent ?: original.contentIntent,
                qzoneDeleteIntent ?: original.deleteIntent)
    }

    /**
     * 获取通知的大图标。
     *
     * @param notification 原有通知。
     * @return 通知的大图标。
     */
    private fun getNotifyLargeIcon(notification: Notification): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notification.getLargeIcon().loadDrawable(this).toBitmap()
        } else {
            @Suppress("DEPRECATION")
            notification.extras.get(Notification.EXTRA_LARGE_ICON) as Bitmap
        }
    }

    /**
     * 发出代理通知。
     *
     * @param tag       来源标记。
     * @param channel   通知渠道。
     * @param style     通知样式。
     * @param id        通知 id.
     * @param largeIcon 大图标。
     * @param original  原始通知。
     */
    private fun sendNotification(tag: Int, channel: NotifyChannel, style: NotificationCompat.Style?,
                                 id: Int, largeIcon: Bitmap?, original: Notification,
                                 contentIntent: PendingIntent, deleteIntent: PendingIntent,
                                 title: String? = null, text: String? = null, ticker: String? = null) {
        val channelId = when (channel) {
            NotifyChannel.FRIEND -> NOTIFY_FRIEND_CHANNEL_ID
            NotifyChannel.GROUP -> NOTIFY_GROUP_CHANNEL_ID
            NotifyChannel.QZONE -> NOTIFY_QZONE_CHANNEL_ID
        }

        @Suppress("DEPRECATION")
        val builder = NotificationCompat.Builder(this, channelId)
                .setColor(resources.getColor(if (channel == NotifyChannel.QZONE)
                    R.color.colorQzone else R.color.colorPrimary))
                .setAutoCancel(true)
                .setShowWhen(true)
                .setStyle(style)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setDeleteIntent(deleteIntent)
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

        setIcon(builder, tag, channel == NotifyChannel.QZONE)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (!isVibrate(this, channel)) {
                builder.setVibrate(longArrayOf(0))
            }
            builder.setSound(getRingtone(this, channel))
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(id, builder.build())
        addNotifyId(tag, id)
    }

    private fun addNotifyId(tag: Int, ids: Int) {
        when (tag) {
            TAG_QQ -> qqNotifyIds.add(ids)
            TAG_TIM -> timNotifyIds.add(ids)
            TAG_QQ_LITE -> qqLiteNotifyIds.add(ids)
            TAG_QZONE -> qzoneNotifyIds.add(ids)
        }
    }

    /**
     * 清除此来源所有通知，并清空历史记录。
     *
     * @param tag 来源标记。
     */
    private fun clearNotification(tag: Int) {
        val ids = when (tag) {
            TAG_QQ -> {
                qqHistory.clear()
                qqNotifyIds
            }
            TAG_QQ_LITE -> {
                qqLiteHistory.clear()
                qqLiteNotifyIds
            }
            TAG_TIM -> {
                timHistory.clear()
                timNotifyIds
            }
            TAG_QZONE -> {
                qzoneHistory.clear()
                qzoneNotifyIds
            }
            else -> null
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).run {
            ids?.forEach { id -> cancel(id) }
            cancel(tag + HIDE_MSG_NOTIFY_ID_OFFSET)
        }
    }

    /**
     * 获取历史消息。
     */
    private fun getHistoryMessage(tag: Int): ArrayList<Conversation> {
        return when (tag) {
            TAG_TIM -> timHistory
            TAG_QQ_LITE -> qqLiteHistory
            else -> qqHistory
        }
    }

    /**
     * 加入历史消息记录。
     */
    private fun addMessage(tag: Int, name: String, content: String, group: String?, icon: Bitmap?,
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
            conversation = Conversation(group != null, group
                    ?: name, contentIntent, deleteIntent)
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
                .setName(name).build()
        val time = System.currentTimeMillis()
    }

    private fun setIcon(builder: NotificationCompat.Builder, tag: Int, isQzone: Boolean) {
        if (isQzone) {
            builder.setSmallIcon(R.drawable.ic_qzone)
            return
        }
        val mode = Integer.parseInt(getIconMode(ctx))
        when (mode) {
            0 -> when (tag) {
                TAG_QQ, TAG_QQ_LITE -> R.drawable.ic_qq
                TAG_TIM -> R.drawable.ic_tim
                else -> R.drawable.ic_qq
            }
            1 -> R.drawable.ic_tim
            2 -> R.drawable.chat2
            3 -> R.drawable.chat
            else -> R.drawable.ic_qq
        }.let { iconRes -> builder.setSmallIcon(iconRes) }
    }
}

