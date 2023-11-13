package cc.chenhe.qqnotifyevo.core

import cc.chenhe.qqnotifyevo.utils.Tag
import timber.log.Timber

/**
 * For com.tencent.tim ver 3.5.5.3198 build 1328.
 *
 * Doesn't support QZone notifications because TIM failed to post anything about QZone.
 */
class TimNotificationResolver : NotificationResolver {
    companion object {
        private const val TAG = "TimNotificationResolver"

        // 隐藏消息详情
        // title: TIM
        // ticker: 你收到了x条新消息
        // text: 你收到了x条新消息

        private const val HIDE_MESSAGE_TITLE = "TIM"
        private val hideMsgTickerPattern = """^你收到了(?<num>\d+)条新消息$""".toRegex()

        // 群聊消息
        // ------------- 单个消息
        // title: 群名
        // ticker: 昵称(群名):消息内容
        // text: [有关注的内容]昵称: 消息内容
        // ------------- 多个消息
        // title: 群名 (x条新消息)
        // ticker: 昵称(群名):消息内容
        // text: [有关注的内容]昵称: 消息内容

        /**
         * 匹配群聊消息 Ticker.
         *
         * 限制：昵称不能包含英文括号 `()`.
         */
        private val groupMsgPattern =
            """^(?<nickname>.+?)\((?<group>.+?)\):(?<msg>[\s\S]+)$""".toRegex()

        private val groupTitlePattern =
            """^(?<group>.+?)(?: \((?<num>\d+)条新消息\))?$""".toRegex()

        /**
         * 匹配群聊消息 Content.
         */
        private val groupMsgContentPattern =
            """^(?<sp>\[有关注的内容])?(?<nickname>.+?): (?<msg>[\s\S]+)$""".toRegex()

        // 私聊消息
        // title: [特别关心]昵称 | [特别关心]昵称 (x条新消息)
        // ticker: 昵称: 消息内容
        // text: 消息内容

        private val privateTitlePattern =
            """^(?<sp>\[特别关心])?(?<nickname>.+?)(?: \((?<num>\d+)条新消息\))?$""".toRegex()


        // 关联QQ消息
        // title: 关联QQ号 | 关联QQ号 (x条新消息)
        // ticker: 关联QQ号-Sender:消息内容
        // text: Sender:消息内容

        private val bindingTitlePattern =
            """^关联QQ号(?: \((?<num>\d+)条新消息\))?$""".toRegex()

        private val bindingTextPattern =
            """^(?<nickname>.+?):(?<msg>[\s\S]+)$""".toRegex()
    }

    override fun resolveNotification(
        tag: Tag,
        title: String?,
        content: String?,
        ticker: String?
    ): QQNotification? {
        if (title.isNullOrEmpty() || content.isNullOrEmpty()) {
            return null
        }
        if (isHidden(title = title, ticker = ticker)) {
            return QQNotification.HiddenMessage(tag)
        }

        if (ticker == null) {
            Timber.tag(TAG).i("Ticker is null, skip")
            return null
        }

        tryResolveBindingMsg(tag, title, content)?.also { return it }
        tryResolveGroupMsg(tag, title, content, ticker)?.also { return it }
        tryResolvePrivateMsg(tag, title, content)?.also { return it }

        return null
    }

    private fun isHidden(title: String?, ticker: String?): Boolean {
        return title == HIDE_MESSAGE_TITLE && ticker != null
                && hideMsgTickerPattern.matchEntire(ticker) != null
    }

    private fun tryResolveGroupMsg(
        tag: Tag,
        title: String,
        content: String,
        ticker: String,
    ): QQNotification? {
        if (content.isEmpty() || ticker.isEmpty()) {
            return null
        }
        val tickerGroups = groupMsgPattern.matchEntire(ticker)?.groups ?: return null
        val titleGroups = groupTitlePattern.matchEntire(title)?.groups ?: return null
        val contentGroups = groupMsgContentPattern.matchEntire(content)?.groups ?: return null
        val name = tickerGroups["nickname"]?.value ?: return null
        val groupName = titleGroups["group"]?.value ?: return null
        val text = contentGroups["msg"]?.value ?: return null
        val special = contentGroups["sp"]?.value != null
        val num = titleGroups["num"]?.value?.toIntOrNull()

        return QQNotification.GroupMessage(
            tag = tag,
            groupName = groupName,
            nickname = name,
            message = text,
            special = special,
            num = num ?: 1,
        )
    }

    private fun tryResolvePrivateMsg(tag: Tag, title: String, content: String): QQNotification? {
        if (title.isEmpty() || content.isEmpty()) {
            return null
        }
        val titleGroups = privateTitlePattern.matchEntire(title)?.groups ?: return null
        val special = titleGroups["sp"] != null
        val name = titleGroups["nickname"]?.value ?: return null
        val num = titleGroups["num"]?.value?.toIntOrNull()

        return QQNotification.PrivateMessage(
            tag = tag,
            nickname = name,
            message = content,
            special = special,
            num = num ?: 1,
        )
    }

    private fun tryResolveBindingMsg(
        tag: Tag,
        title: String,
        content: String
    ): QQNotification? {
        val titleGroups = bindingTitlePattern.matchEntire(title)?.groups ?: return null
        val textGroups = bindingTextPattern.matchEntire(content)?.groups ?: return null

        val sender = textGroups["nickname"]?.value ?: return null
        val text = textGroups["msg"]?.value ?: return null
        val num = titleGroups["num"]?.value?.toIntOrNull()
        return QQNotification.BindingAccountMessage(tag, sender, text, num ?: 1)
    }
}