package cc.chenhe.qqnotifyevo.core

import android.app.Notification
import android.service.notification.StatusBarNotification
import cc.chenhe.qqnotifyevo.BuildConfig
import cc.chenhe.qqnotifyevo.utils.Tag
import org.json.JSONObject
import timber.log.Timber

/**
 * 已知的 QQ 通知种类
 */
sealed class QQNotification {
    abstract val tag: Tag

    /** 隐藏了消息内容的通知 */
    data class HiddenMessage(override val tag: Tag) : QQNotification()

    /** QQ 空间特别关心动态推送 */
    data class QZoneSpecialPost(override val tag: Tag, val content: String) : QQNotification()

    /** QQ 空间动态：点赞评论等 */
    data class QZoneMessage(override val tag: Tag, val content: String, val num: Int) :
        QQNotification()

    /**
     * 群聊消息
     * @param nickname 消息发送者昵称，通常是在群聊中的昵称
     * @param special 特别关心
     */
    data class GroupMessage(
        override val tag: Tag,
        val groupName: String,
        val nickname: String,
        val message: String,
        val special: Boolean,
        val num: Int,
    ) : QQNotification()

    /**
     * 私聊消息
     * @param special 特别关心
     */
    data class PrivateMessage(
        override val tag: Tag,
        val nickname: String,
        val message: String,
        val special: Boolean,
        val num: Int,
    ) : QQNotification()

    /**
     * 来自关联账号的消息
     * @param sender 消息发送者昵称，不是被关联账号的昵称
     */
    data class BindingAccountMessage(
        override val tag: Tag,
        val sender: String,
        val message: String,
        val num: Int,
    ) : QQNotification()
}

private const val TAG = "NotificationResolver"

/**
 * A resolver that can parse arbitrary notification from QQ (or TIM e.g.) into a known pattern.
 * Not responsible for managing history. In general, different implementations work for different
 * source APPs and versions.
 */
interface NotificationResolver {

    /**
     * Resolve the given notification into a known pattern.
     * @return resolved pattern, `null` if not matched.
     */
    fun resolveNotification(
        packageName: String,
        tag: Tag,
        sbn: StatusBarNotification
    ): QQNotification? {
        val original = sbn.notification ?: return null
        val title = original.extras.getString(Notification.EXTRA_TITLE)
        val content = original.extras.getString(Notification.EXTRA_TEXT)
        val ticker = original.tickerText?.toString()

        if (BuildConfig.DEBUG) {
            val jsonStr = JSONObject().apply {
                put("title", title)
                put("ticker", ticker)
                put("content", content)
            }.toString()
            Timber.tag(TAG).v(jsonStr)
        }

        Timber.tag(TAG).v("Title: $title; Ticker: $ticker; Content: $content")
        return resolveNotification(tag, title, content, ticker)
    }

    /**
     * Resolve the given notification components into a known pattern.
     * @return resolved pattern, `null` if not matched.
     */
    fun resolveNotification(
        tag: Tag,
        title: String?,
        content: String?,
        ticker: String?,
    ): QQNotification?
}