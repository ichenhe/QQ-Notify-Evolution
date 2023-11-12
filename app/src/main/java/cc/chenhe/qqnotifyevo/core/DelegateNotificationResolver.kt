package cc.chenhe.qqnotifyevo.core

import cc.chenhe.qqnotifyevo.utils.Tag

/**
 * A [NotificationResolver] that call different implementations based on [Tag].
 */
class DelegateNotificationResolver : NotificationResolver {
    private val qqResolver by lazy { QQNotificationResolver() }
    private val timResolver by lazy { TimNotificationResolver() }

    override fun resolveNotification(
        tag: Tag,
        title: String?,
        content: String?,
        ticker: String?
    ): QQNotification? {
        return when (tag) {
            Tag.UNKNOWN -> null
            Tag.QQ -> qqResolver
            Tag.QQ_HD -> qqResolver
            Tag.QQ_LITE -> qqResolver
            Tag.TIM -> timResolver
        }?.run { resolveNotification(tag, title, content, ticker) }
    }
}