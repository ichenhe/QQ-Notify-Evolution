package cc.chenhe.qqnotifyevo.core

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.edit
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.ui.MainActivity
import cc.chenhe.qqnotifyevo.utils.*
import cc.chenhe.qqnotifyevo.utils.SpecialGroupChannel.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.*

abstract class NotificationProcessor(context: Context, scope: CoroutineScope) {

    companion object {
        private const val TAG = "NotificationProcessor"

        /**
         * 用于在优化后的通知中保留原始来源标记。通过 [Notification.extras] 提取。
         *
         * 值为 [String] 类型，关联于 [Tag].
         */
        const val NOTIFICATION_EXTRA_TAG = "qqevo.tag"

        private const val CONVERSATION_NAME_QZONE = "QZone"
        private const val CONVERSATION_NAME_QZONE_SPECIAL = "QZoneSpecial" // 特别关心空间动态推送


        fun getTagFromPackageName(packageName: String): Tag {
            return when (packageName) {
                "com.tencent.mobileqq" -> Tag.QQ
                "com.tencent.tim" -> Tag.TIM
                "com.tencent.qqlite" -> Tag.QQ_LITE
                "com.tencent.minihd.qq" -> Tag.QQ_HD
                else -> Tag.UNKNOWN
            }
        }
    }

    protected val ctx: Context = context.applicationContext
    private var iconStyle: IconStyle = IconStyle.Auto
    private var nicknameFormat: String = PREFERENCE_NICKNAME_FORMAT_DEFAULT
    private var formatNickname: Boolean = PREFERENCE_FORMAT_NICKNAME_DEFAULT
    private var showSpecialPrefix: Boolean = PREFERENCE_SHOW_SPECIAL_PREFIX_DEFAULT
    private var specialGroupChannel: SpecialGroupChannel = PREFERENCE_SPECIAL_GROUP_CHANNEL_DEFAULT

    private val qzoneSpecialTitle = context.getString(R.string.notify_qzone_special_title)

    private val qqHistory = ArrayList<Conversation>()
    private val qqLiteHistory = ArrayList<Conversation>()
    private val qqHdHistory = ArrayList<Conversation>()
    private val timHistory = ArrayList<Conversation>()

    private val avatarManager =
        AvatarManager.get(getAvatarDiskCacheDir(ctx), getAvatarCachePeriod(context))
    private val resolver: NotificationResolver = DelegateNotificationResolver()

    init {
        scope.launch {
            context.dataStore.data.collect { pref ->
                iconStyle = IconStyle.fromValue(pref[PREFERENCE_ICON])
                nicknameFormat =
                    pref[PREFERENCE_NICKNAME_FORMAT] ?: PREFERENCE_NICKNAME_FORMAT_DEFAULT
                formatNickname =
                    pref[PREFERENCE_FORMAT_NICKNAME] ?: PREFERENCE_FORMAT_NICKNAME_DEFAULT
                showSpecialPrefix =
                    pref[PREFERENCE_SHOW_SPECIAL_PREFIX] ?: PREFERENCE_SHOW_SPECIAL_PREFIX_DEFAULT
                specialGroupChannel =
                    SpecialGroupChannel.fromValue(pref[PREFERENCE_SPECIAL_GROUP_CHANNEL])
                avatarManager.period = AvatarCacheAge.fromValue(pref[PREFERENCE_AVATAR_CACHE_AGE]).v
            }
        }
    }

    /**
     * 清空此来源所有会话（包括 QQ 空间）历史记录。
     *
     * @param tag 来源标记。
     */
    fun clearHistory(tag: Tag) {
        Timber.tag(TAG).v("Clear history. tag=$tag")
        getHistoryMessage(tag).clear()
    }

    /**
     * 清空此来源特别关心 QQ 空间动态推送历史记录。不清除与我相关的动态或其他聊天消息。
     *
     * @param tag 来源标记。
     */
    private fun clearQzoneSpecialHistory(tag: Tag) {
        Timber.tag(TAG).d("Clear QZone history. tag=$tag")
        getHistoryMessage(tag).removeIf {
            it.name == qzoneSpecialTitle
        }
    }

    /**
     * 检测到合并消息的回调。
     *
     * 合并消息：有 x 个联系人给你发过来y条新消息
     *
     * @param isBindingMsg 是否来自关联 QQ 的消息。
     */
    protected open fun onMultiMessageDetected(isBindingMsg: Boolean) {}

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
        tag: Tag,
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
        tag: Tag,
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
    fun resolveNotification(
        context: Context,
        packageName: String,
        sbn: StatusBarNotification
    ): Notification? {
        val original = sbn.notification ?: return null
        val tag = getTagFromPackageName(packageName)
        if (tag == Tag.UNKNOWN) {
            return null
        }

        return when (val r = resolver.resolveNotification(packageName, tag, sbn)) {
            is QQNotification.BindingAccountMessage -> {
                val conversation = addMessage(
                    tag = r.tag,
                    name = context.getString(R.string.notify_binding_msg_title, r.sender),
                    content = r.message,
                    group = null,
                    icon = getNotifyLargeIcon(context, original),
                    contentIntent = original.contentIntent,
                    deleteIntent = original.deleteIntent,
                    special = false,
                )
                deleteOldMessage(conversation, r.num)
                Timber.tag(TAG).d("[Binding] Sender: ${r.sender}; Text: ${r.message}")
                val channel = NotifyChannel.FRIEND
                renewConversionNotification(context, r.tag, channel, conversation, sbn, original)
            }

            is QQNotification.GroupMessage -> {
                if (r.num == 1) {
                    // 单个消息
                    getNotifyLargeIcon(context, original)?.also {
                        avatarManager.saveAvatar(r.groupName.hashCode(), it)
                    }
                }
                val conversation = addMessage(
                    tag = r.tag,
                    name = r.nickname,
                    content = r.message,
                    group = r.groupName,
                    icon = avatarManager.getAvatar(r.nickname.hashCode()),
                    contentIntent = original.contentIntent,
                    deleteIntent = original.deleteIntent,
                    special = r.special,
                )
                if (r.num > 1) {
                    deleteOldMessage(conversation, r.num)
                }
                Timber.tag(TAG)
                    .d("[${if (r.special) "GroupS" else "Group"}] Name: ${r.nickname}; Group: ${r.groupName}; Text: ${r.message}")
                val channel = if (r.special) {
                    when (specialGroupChannel) {
                        Group -> NotifyChannel.GROUP
                        Special -> NotifyChannel.FRIEND_SPECIAL
                    }
                } else {
                    NotifyChannel.GROUP
                }
                renewConversionNotification(context, r.tag, channel, conversation, sbn, original)
            }

            is QQNotification.HiddenMessage -> {
                Timber.tag(TAG).v("Hidden message content, skip")
                null
            }

            is QQNotification.PrivateMessage -> {
                if (r.num == 1) {
                    // 单个消息
                    getNotifyLargeIcon(context, original)?.also {
                        avatarManager.saveAvatar(r.nickname.hashCode(), it)
                    }
                }
                val conversation = addMessage(
                    tag = r.tag,
                    name = r.nickname,
                    content = r.message,
                    group = null,
                    icon = avatarManager.getAvatar(r.nickname.hashCode()),
                    contentIntent = original.contentIntent,
                    deleteIntent = original.deleteIntent,
                    special = r.special,
                )
                if (r.num > 1) {
                    deleteOldMessage(conversation, r.num)
                }
                val prefix = if (r.special) "[FriendS]" else "[Friend]"
                Timber.tag(TAG).d("$prefix Name: ${r.nickname}; Text: ${r.message}")
                val channel = if (r.special) {
                    NotifyChannel.FRIEND_SPECIAL
                } else {
                    NotifyChannel.FRIEND
                }
                renewConversionNotification(context, r.tag, channel, conversation, sbn, original)
            }

            is QQNotification.QZoneMessage -> {
                getNotifyLargeIcon(context, original)?.also {
                    avatarManager.saveAvatar(CONVERSATION_NAME_QZONE.hashCode(), it)
                }
                val conversation = addMessage(
                    tag = r.tag,
                    name = context.getString(R.string.notify_qzone_title),
                    content = r.content,
                    group = null,
                    icon = avatarManager.getAvatar(CONVERSATION_NAME_QZONE.hashCode()),
                    contentIntent = original.contentIntent,
                    deleteIntent = original.deleteIntent,
                    special = false
                )
                deleteOldMessage(conversation, r.num)
                Timber.tag(TAG).d("[QZone] content: ${r.content}")
                renewQzoneNotification(context, r.tag, conversation, sbn, original)
            }

            is QQNotification.QZoneSpecialPost -> {
                getNotifyLargeIcon(context, original)?.also {
                    avatarManager.saveAvatar(CONVERSATION_NAME_QZONE_SPECIAL.hashCode(), it)
                }
                val conversation = addMessage(
                    tag = r.tag,
                    name = qzoneSpecialTitle,
                    content = r.content,
                    group = null,
                    icon = avatarManager.getAvatar(CONVERSATION_NAME_QZONE_SPECIAL.hashCode()),
                    contentIntent = original.contentIntent,
                    deleteIntent = original.deleteIntent,
                    special = false
                )
                // 由于特别关心动态推送的通知没有显示未读消息个数，所以这里无法提取并删除多余的历史消息。
                // Workaround: 在通知删除回调下来匹配并清空特别关心动态历史记录。
                Timber.tag(TAG).d("[QZoneSpecial] content: ${r.content}")
                renewQzoneNotification(context, r.tag, conversation, sbn, original)
            }

            null -> {
                Timber.tag(TAG).w("[None] Not match any pattern")
                null
            }
        }

    }


    fun onNotificationRemoved(sbn: StatusBarNotification, reason: Int) {
        val tag =
            Tag.valueOf(sbn.notification.extras.getString(NOTIFICATION_EXTRA_TAG, Tag.UNKNOWN.name))
        if (tag == Tag.UNKNOWN) return
        val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)
        Timber.tag(TAG).v("onNotificationRemoved: Tag=$tag, Reason=$reason, Title=$title")
        if (title == qzoneSpecialTitle) {
            // 清除 QQ 空间特别关心动态推送历史记录
            clearQzoneSpecialHistory(tag)
        }
        // 清除关联的 long live shortcut
        // 因为 QQ 的限制，shortcut 并不能直接跳转对话框，仅用于满足 Android 11 「会话」通知的要求
        // 所以保留它没有任何意义
        sbn.notification.shortcutId?.also { shortcutId ->
            if (shortcutId.isNotEmpty()) {
                ShortcutManagerCompat.removeLongLivedShortcuts(ctx, listOf(shortcutId))
            }
        }
    }

    /**
     * 获取通知的大图标。
     *
     * @param notification 原有通知。
     * @return 通知的大图标。
     */
    private fun getNotifyLargeIcon(context: Context, notification: Notification): Bitmap? {
        return notification.getLargeIcon()?.loadDrawable(context)?.toBitmap()
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
        tag: Tag,
        channel: NotifyChannel,
        style: NotificationCompat.Style?,
        largeIcon: Bitmap?,
        original: Notification,
        subtext: String? = null,
        title: String? = null, text: String? = null, ticker: String? = null,
        shortcutInfo: ShortcutInfoCompat? = null
    ): Notification {
        val channelId = getChannelId(channel)

        val color = ContextCompat.getColor(
            context,
            if (channel == NotifyChannel.QZONE) R.color.colorQzoneIcon else R.color.colorConversationIcon
        )

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

        setIcon(builder, tag, channel == NotifyChannel.QZONE)

        return buildNotification(builder, shortcutInfo).apply {
            extras.putString(NOTIFICATION_EXTRA_TAG, tag.name)
        }
    }

    protected open fun buildNotification(
        builder: NotificationCompat.Builder,
        shortcutInfo: ShortcutInfoCompat?
    ): Notification {
        return builder.build()
    }

    protected fun createQZoneNotification(
        context: Context, tag: Tag, conversation: Conversation,
        original: Notification
    ): Notification {
        val style = NotificationCompat.MessagingStyle(
            Person.Builder()
                .setName(context.getString(R.string.notify_qzone_title)).build()
        )
        conversation.messages.forEach { msg ->
            style.addMessage(msg)
        }
        val num = conversation.messages.size
        val subtext =
            if (num > 1) context.getString(R.string.notify_subtext_qzone_num, num) else null
        Timber.tag(TAG).v("Create QZone notification for $num messages.")
        return createNotification(
            context, tag, NotifyChannel.QZONE, style,
            avatarManager.getAvatar(CONVERSATION_NAME_QZONE.hashCode()), original, subtext
        )
    }


    /**
     * 创建会话消息通知。
     *
     * @param tag      来源标记。
     * @param original 原始通知。
     */
    @SuppressLint("BinaryOperationInTimber")
    protected fun createConversationNotification(
        context: Context, tag: Tag, channel: NotifyChannel,
        conversation: Conversation, original: Notification
    ): Notification {
        val style =
            NotificationCompat.MessagingStyle(Person.Builder().setName(conversation.name).build())
        if (conversation.isGroup) {
            style.conversationTitle = conversation.name
            style.isGroupConversation = true
        }
        conversation.messages.forEach { msg ->
            style.addMessage(msg)
        }
        val num = conversation.messages.size
        val subtext =
            if (num > 1) context.getString(R.string.notify_subtext_message_num, num) else null
        Timber.tag(TAG).v("Create conversation notification for $num messages.")

        val shortcut = ShortcutInfoCompat.Builder(context, conversation.name)
            .setIsConversation()
            .setPersons(conversation.messages.map { it.person }.toSet().toTypedArray())
            .setShortLabel(conversation.name)
            .setLongLabel(conversation.name)
            .setIcon(
                avatarManager.getAvatar(conversation.name.hashCode())
                    ?.let { IconCompat.createWithBitmap(it) }
            )
            .setIntent(
                context.packageManager.getLaunchIntentForPackage(tag.pkg)
                    ?: Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_MAIN
                    }
            )
            .build()
        return createNotification(
            context, tag, channel, style,
            avatarManager.getAvatar(conversation.name.hashCode()), original, subtext,
            shortcutInfo = shortcut
        )
    }

    private fun NotificationCompat.MessagingStyle.addMessage(message: Message) {
        var name = message.person.name!!

        name = formatNicknameIfNeeded(name)

        if (message.special && showSpecialPrefix) {
            // 添加特别关心或关注前缀
            name = if (isGroupConversation)
                ctx.getString(R.string.special_group_prefix) + name
            else
                ctx.getString(R.string.special_prefix) + name
        }

        val person = if (name == message.person.name) {
            message.person
        } else {
            message.person.clone(name)
        }
        addMessage(message.content, message.time, person)
    }

    private fun formatNicknameIfNeeded(name: CharSequence): CharSequence {
        if (!formatNickname) {
            return name
        }
        val newName = nicknameFormat.replace("\$n", name.toString())
        if (newName == nicknameFormat) {
            Timber.tag(TAG).e("Invalid nickname format, reset it. format=$nicknameFormat")
            runBlocking {
                ctx.dataStore.edit {
                    it[PREFERENCE_NICKNAME_FORMAT] = PREFERENCE_NICKNAME_FORMAT_DEFAULT
                }
            }
            return name
        }
        return newName
    }

    private fun Person.clone(newName: CharSequence? = null): Person {
        return Person.Builder()
            .setBot(this.isBot)
            .setIcon(this.icon)
            .setImportant(this.isImportant)
            .setKey(this.key)
            .setName(newName ?: this.name)
            .setUri(this.uri)
            .build()
    }

    private fun setIcon(
        builder: NotificationCompat.Builder,
        tag: Tag,
        isQzone: Boolean
    ) {
        if (isQzone) {
            builder.setSmallIcon(R.drawable.ic_notify_qzone)
            return
        }
        when (iconStyle) {
            IconStyle.Auto -> when (tag) {
                Tag.QQ, Tag.QQ_HD, Tag.QQ_LITE -> R.drawable.ic_notify_qq
                Tag.TIM -> R.drawable.ic_notify_tim
                else -> R.drawable.ic_notify_qq
            }

            IconStyle.QQ -> R.drawable.ic_notify_qq
            IconStyle.TIM -> R.drawable.ic_notify_tim
        }.let { iconRes -> builder.setSmallIcon(iconRes) }
    }


    /**
     * 获取历史消息。
     */
    protected fun getHistoryMessage(tag: Tag): ArrayList<Conversation> {
        return when (tag) {
            Tag.TIM -> timHistory
            Tag.QQ_LITE -> qqLiteHistory
            Tag.QQ -> qqHistory
            Tag.QQ_HD -> qqHdHistory
            else -> throw RuntimeException("Unknown tag: $tag.")
        }
    }

    /**
     * 加入历史消息记录。
     *
     * @param name 发送者昵称。
     * @param content 消息内容。
     * @param group 群组名。`null` 表示非群组消息。
     * @param special 是否来自特别关心或特别关注。
     */
    private fun addMessage(
        tag: Tag, name: String, content: String, group: String?, icon: Bitmap?,
        contentIntent: PendingIntent, deleteIntent: PendingIntent, special: Boolean
    ): Conversation {
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
        conversation.messages.add(Message(name, icon, content, special))
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
        Timber.tag(TAG)
            .d("Delete old messages. conversation: ${conversation.name}, max: $maxMessageNum")
        while (conversation.messages.size > maxMessageNum) {
            conversation.messages.removeAt(0)
        }
    }

    protected data class Conversation(
        val isGroup: Boolean,
        val name: String,
        var contentIntent: PendingIntent,
        var deleteIntent: PendingIntent
    ) {
        val messages = ArrayList<Message>()
    }

    /**
     * @param name 发送者昵称。
     * @param icon 头像。
     * @param content 消息内容。
     * @param special 是否来自特别关心或特别关注。仅在聊天消息中有效。
     */
    protected data class Message(
        val name: String,
        val icon: Bitmap?,
        val content: String,
        val special: Boolean
    ) {
        val person: Person = Person.Builder()
            .setIcon(icon?.let { IconCompat.createWithBitmap(it) })
            .setName(name)
            .build()
        val time = System.currentTimeMillis()
    }
}