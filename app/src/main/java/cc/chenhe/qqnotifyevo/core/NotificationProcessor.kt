package cc.chenhe.qqnotifyevo.core

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.preference.PreferenceAty
import cc.chenhe.qqnotifyevo.utils.*
import timber.log.Timber
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

abstract class NotificationProcessor(context: Context) {

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

        // 群聊消息
        // title: 群名 | 群名 (x条新消息)
        // ticker: 昵称(群名):消息内容
        // text: 昵称: 消息内容 //特别关注前缀：[有关注的内容]
        // QQHD v5.8.8.3445 中群里特别关心前缀为 特别关注。

        /**
         * 匹配群聊消息 Ticker.
         *
         * Group: 1昵称, 2群名, 3消息内容
         *
         * 限制：昵称不能包含英文括号 `()`.
         */
        @VisibleForTesting
        val groupMsgPattern: Pattern = Pattern.compile("^(.+?)\\((.+)\\):([\\s\\S]+)$")

        /**
         * 匹配群聊消息 Content.
         *
         * Group: 1[有关注的内容
         *
         * QQHD v5.8.8.3445 中群里特别关心前缀为 特别关注。
         */
        @VisibleForTesting
        val groupMsgContentPattern: Pattern = Pattern.compile("^(\\[(?:有关注的内容|特别关注)])?[\\s\\S]+")

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
        val msgPattern: Pattern = Pattern.compile("^(.+?): ([\\s\\S]+)$")

        /**
         * 匹配私聊消息 Title.
         *
         * Group: 1\[特别关心\], 2新消息数目
         */
        @VisibleForTesting
        val msgTitlePattern: Pattern = Pattern.compile("^(\\[特别关心])?.*?(?: \\((\\d+)条新消息\\))?$")

        // 关联QQ消息
        // title:
        //      - 只有一条消息: 关联QQ号
        //      - 一人发来多条消息: 关联QQ号 ({x}条新消息)
        //      - 多人发来消息: QQ
        // ticker:  关联QQ号-{关联昵称} {发送者昵称}:{消息内容}
        // content:
        //      - 一人发来消息: {消息内容}
        //      - 多人发来消息: 有 {x} 个联系人给你发过来{y}条新消息

        /**
         * 匹配关联 QQ 消息 ticker.
         *
         * Group: 1关联账号昵称, 2消息内容
         */
        @VisibleForTesting
        val bindingQQMsgTickerPattern: Pattern = Pattern.compile("^关联QQ号-(.+?):([\\s\\S]+)$")

        /**
         * 匹配关联 QQ 消息 content. 用于提取未读消息个数。
         *
         * Group: 1未读消息个数
         */
        @VisibleForTesting
        val bindingQQMsgContextPattern: Pattern =
            Pattern.compile("^有 (?:\\d+) 个联系人给你发过来(\\d+)条新消息$")

        /**
         * 匹配关联 QQ 消息 title. 用于提取未读消息个数。
         *
         * Group: 1未读消息个数
         */
        @VisibleForTesting
        val bindingQQMsgTitlePattern: Pattern = Pattern.compile("^关联QQ号 \\((\\d+)条新消息\\)$")

        // Q空间动态
        // title(与我相关): QQ空间动态(共x条未读); (特别关心): QQ空间动态
        // ticker(与我相关): 详情（例如xxx评论了你）; (特别关心): 【特别关心】昵称：内容
        // text: 与 ticker 相同
        // 注意：与我相关动态、特别关心动态是两个独立的通知，不会互相覆盖。

        /**
         * 匹配 QQ 空间 Title.
         *
         * Group: 1新消息数目
         */
        @VisibleForTesting
        val qzonePattern: Pattern = Pattern.compile("^QQ空间动态(?:\\(共(\\d+)条未读\\))?$")

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

    protected val ctx: Context = context.applicationContext

    private val qzoneSpecialTitle = context.getString(R.string.notify_qzone_special_title)

    private val qqHistory = ArrayList<Conversation>()
    private val qqLiteHistory = ArrayList<Conversation>()
    private val qqHdHistory = ArrayList<Conversation>()
    private val timHistory = ArrayList<Conversation>()

    private val avatarManager =
        AvatarManager.get(getAvatarDiskCacheDir(ctx), getAvatarCachePeriod(context))

    fun setAvatarCachePeriod(period: Long) {
        avatarManager.period = period
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
            Timber.tag(TAG).d("Unknown tag, skip. pkgName=$packageName")
            return null
        }

        val title = original.extras.getString(Notification.EXTRA_TITLE)
        val content = original.extras.getString(Notification.EXTRA_TEXT)
        val ticker = original.tickerText?.toString()

        val isMulti = isMulti(ticker, content)
        val isQzone = isQzone(title)

        Timber.tag(TAG)
            .v("Title: $title; Ticker: $ticker; QZone: $isQzone; Multi: $isMulti; Content: $content")

        if (isMulti) {
            onMultiMessageDetected(ticker?.contains("关联QQ号-") ?: false)
        }

        // 隐藏消息详情
        if (isHidden(ticker, content)) {
            Timber.tag(TAG).v("Hidden message content, skip.")
            return null
        }

        // QQ空间
        tryResolveQzone(
            context,
            tag,
            original,
            isQzone,
            title,
            ticker,
            content
        )?.also { conversation ->
            return renewQzoneNotification(context, tag, conversation, sbn, original)
        }

        if (ticker == null) {
            Timber.tag(TAG).i("Ticker is null, skip.")
            return null
        }

        // 群消息
        tryResolveGroupMsg(
            context,
            tag,
            original,
            isMulti,
            title,
            ticker,
            content
        )?.also { (channel, conversation) ->
            return renewConversionNotification(context, tag, channel, conversation, sbn, original)
        }

        // 私聊消息
        tryResolvePrivateMsg(
            context,
            tag,
            original,
            isMulti,
            title,
            ticker
        )?.also { (channel, conversation) ->
            return renewConversionNotification(context, tag, channel, conversation, sbn, original)
        }

        // 关联账号消息
        tryResolveBindingMsg(
            context,
            tag,
            original,
            title,
            ticker,
            content
        )?.also { (channel, conversation) ->
            return renewConversionNotification(context, tag, channel, conversation, sbn, original)
        }

        Timber.tag(TAG).w("[None] Not match any pattern.")
        return null
    }

    private fun isMulti(ticker: String?, content: String?): Boolean {
        // 合并消息
        // title: QQ
        // ticker: 昵称:内容
        // text: 有 x 个联系人给你发过来y条新消息
        return content?.let {
            !it.contains(":") && !(ticker?.endsWith(it) ?: false)
        } ?: false
    }

    private fun isQzone(title: String?): Boolean {
        return title?.let { qzonePattern.matcher(it).matches() } ?: false
    }

    private fun isHidden(ticker: String?, content: String?): Boolean {
        return ticker != null && ticker == content && hideMsgPattern.matcher(ticker).matches()
    }

    private fun tryResolveQzone(
        context: Context, tag: Tag, original: Notification, isQzone: Boolean, title: String?,
        ticker: String?, content: String?
    ): Conversation? {
        if (isQzone && !content.isNullOrEmpty()) {
            val num = matchQzoneNum(title)
            val conversation: Conversation
            if (num == -1) {
                // 特别关心动态推送
                getNotifyLargeIcon(context, original)?.also {
                    avatarManager.saveAvatar(CONVERSATION_NAME_QZONE_SPECIAL.hashCode(), it)
                }
                conversation = addMessage(
                    tag,
                    qzoneSpecialTitle,
                    content,
                    null,
                    avatarManager.getAvatar(CONVERSATION_NAME_QZONE_SPECIAL.hashCode()),
                    original.contentIntent,
                    original.deleteIntent,
                    false
                )
                // 由于特别关心动态推送的通知没有显示未读消息个数，所以这里无法提取并删除多余的历史消息。
                // Workaround: 在通知删除回调下来匹配并清空特别关心动态历史记录。
                Timber.tag(TAG).d("[QZoneSpecial] Ticker: $ticker")
            } else {
                // 与我相关的动态
                getNotifyLargeIcon(context, original)?.also {
                    avatarManager.saveAvatar(CONVERSATION_NAME_QZONE.hashCode(), it)
                }
                conversation = addMessage(
                    tag,
                    context.getString(R.string.notify_qzone_title),
                    content,
                    null,
                    avatarManager.getAvatar(CONVERSATION_NAME_QZONE.hashCode()),
                    original.contentIntent,
                    original.deleteIntent,
                    false
                )
                deleteOldMessage(conversation, num)
                Timber.tag(TAG).d("[QZone] Ticker: $ticker")
            }
            return conversation
        }
        return null
    }

    private fun tryResolveGroupMsg(
        context: Context, tag: Tag, original: Notification, isMulti: Boolean, title: String?,
        ticker: String, content: String?
    ): Pair<NotifyChannel, Conversation>? {
        groupMsgPattern.matcher(ticker).also { matcher ->
            if (matcher.matches()) {
                val name = matcher.group(1) ?: return null
                val groupName = matcher.group(2) ?: return null
                val text = matcher.group(3) ?: return null

                val contentMatcher = groupMsgContentPattern.matcher(content!!)
                val special = contentMatcher.matches() && contentMatcher.group(1) != null

                if (!isMulti)
                    getNotifyLargeIcon(context, original)?.also {
                        avatarManager.saveAvatar(groupName.hashCode(), it)
                    }
                val conversation = addMessage(
                    tag, name, text, groupName, avatarManager.getAvatar(name.hashCode()),
                    original.contentIntent, original.deleteIntent, special
                )
                deleteOldMessage(conversation, if (isMulti) 0 else matchMessageNum(title))
                Timber.tag(TAG)
                    .d("[${if (special) "GroupS" else "Group"}] Name: $name; Group: $groupName; Text: $text")
                val channel = if (special && specialGroupMsgChannel(ctx))
                    NotifyChannel.FRIEND_SPECIAL
                else
                    NotifyChannel.GROUP
                return Pair(channel, conversation)
            }
        }
        return null
    }

    private fun tryResolvePrivateMsg(
        context: Context, tag: Tag, original: Notification, isMulti: Boolean,
        title: String?, ticker: String
    ): Pair<NotifyChannel, Conversation>? {
        msgPattern.matcher(ticker).also { matcher ->
            if (matcher.matches()) {
                val titleMatcher = msgTitlePattern.matcher(title ?: "")
                val special = titleMatcher.matches() && titleMatcher.group(1) != null
                val name = matcher.group(1) ?: return null
                val text = matcher.group(2) ?: return null
                if (!isMulti)
                    getNotifyLargeIcon(context, original)?.also {
                        avatarManager.saveAvatar(name.hashCode(), it)
                    }
                val conversation = addMessage(
                    tag, name, text, null, avatarManager.getAvatar(name.hashCode()),
                    original.contentIntent, original.deleteIntent, special
                )
                deleteOldMessage(conversation, if (isMulti) 0 else matchMessageNum(titleMatcher))
                return if (special) {
                    Timber.tag(TAG).d("[FriendS] Name: $name; Text: $text")
                    Pair(NotifyChannel.FRIEND_SPECIAL, conversation)
                } else {
                    Timber.tag(TAG).d("[Friend] Name: $name; Text: $text")
                    Pair(NotifyChannel.FRIEND, conversation)
                }
            }
        }
        return null
    }

    private fun tryResolveBindingMsg(
        context: Context, tag: Tag, original: Notification, title: String?,
        ticker: String, content: String?
    ): Pair<NotifyChannel, Conversation>? {
        bindingQQMsgTickerPattern.matcher(ticker).also { matcher ->
            if (matcher.matches()) {
                val account = matcher.group(1) ?: ""
                val text = matcher.group(2) ?: return null
                val conversation = addMessage(
                    tag, context.getString(R.string.notify_binding_msg_title, account),
                    text, null, getNotifyLargeIcon(context, original), original.contentIntent,
                    original.deleteIntent, false
                )
                deleteOldMessage(conversation, matchBindingMsgNum(title, content))
                Timber.tag(TAG).d("[Binding] Account: $account; Text: $text")
                return Pair(NotifyChannel.FRIEND, conversation)
            }
        }
        return null
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
     *
     * @return 动态未读消息个数。若是特别关心推送则返回 `-1`。[title] 为空或不匹配则返回 `0`。
     */
    private fun matchQzoneNum(title: String?): Int {
        if (title.isNullOrEmpty()) return 0
        qzonePattern.matcher(title).also { matcher ->
            if (matcher.matches()) {
                return matcher.group(1)?.toInt() ?: -1
            }
        }
        return 0
    }

    /**
     * 提取关联账号的未读消息个数。
     */
    private fun matchBindingMsgNum(title: String?, content: String?): Int {
        if (title == null || content == null) return 1
        if (title == "QQ") {
            bindingQQMsgContextPattern.matcher(content).also { matcher ->
                if (matcher.matches()) {
                    return matcher.group(1)?.toInt() ?: 1
                }
            }
        } else {
            bindingQQMsgTitlePattern.matcher(title).also { matcher ->
                if (matcher.matches()) {
                    return matcher.group(1)?.toInt() ?: 1
                }
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
        shortcutInfo?.also { builder.setShortcutInfo(it) }

        setIcon(context, builder, tag, channel == NotifyChannel.QZONE)

        return builder.build().apply {
            extras.putString(NOTIFICATION_EXTRA_TAG, tag.name)
        }
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
                    ?: Intent(context, PreferenceAty::class.java).apply {
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

        if (message.special && showSpecialPrefix(ctx)) {
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
        if (!wrapNickname(ctx)) {
            return name
        }
        var newName = name
        val wrapper = nicknameWrapper(ctx)
        if (wrapper != null) {
            newName = wrapper.replace("\$n", name.toString())
            if (newName == wrapper) {
                Timber.tag(TAG).e("Nickname wrapper is invalid, reset preference. wrapper=$wrapper")
                resetNicknameWrapper(ctx)
            }
        } else {
            Timber.tag(TAG).e("Nickname wrapper is null, reset preference.")
            resetNicknameWrapper(ctx)
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
        context: Context,
        builder: NotificationCompat.Builder,
        tag: Tag,
        isQzone: Boolean
    ) {
        if (isQzone) {
            builder.setSmallIcon(R.drawable.ic_notify_qzone)
            return
        }
        when (getIconMode(context)) {
            ICON_AUTO -> when (tag) {
                Tag.QQ, Tag.QQ_HD, Tag.QQ_LITE -> R.drawable.ic_notify_qq
                Tag.TIM -> R.drawable.ic_notify_tim
                else -> R.drawable.ic_notify_qq
            }
            ICON_QQ -> R.drawable.ic_notify_qq
            ICON_TIM -> R.drawable.ic_notify_tim
            else -> R.drawable.ic_notify_qq
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