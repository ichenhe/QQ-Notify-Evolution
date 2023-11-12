package cc.chenhe.qqnotifyevo.core

import cc.chenhe.qqnotifyevo.utils.Tag
import timber.log.Timber
import java.util.regex.Pattern

/**
 * For com.tencent.mobileqq ver 8.9.85.12820 build 4766
 */
class QQNotificationResolver : NotificationResolver {
    companion object {
        private const val TAG = "QQNotificationResolver"
        // Q空间动态
        // --------------- 说说评论/点赞
        // title: QQ空间动态(共1条未读)
        // ticker: XXX评论了你 | XXX赞了你的说说
        // content: XXX评论了你 | XXX赞了你的说说

        // --------------- 特别关心动态通知
        // title: QQ空间动态
        // ticker: 【特别关心】昵称：动态内容
        // content: 【特别关心】昵称：动态内容

        // 注意：与我相关动态、特别关心动态是两个独立的通知，不会互相覆盖。

        /**
         * 匹配 QQ 空间 Title.
         *
         * Group: 1新消息数目
         */
        private val qzoneTitlePattern: Pattern =
            Pattern.compile("^QQ空间动态(?:\\(共(\\d+)条未读\\))?$")


        // 隐藏消息详情
        // title: QQ
        // ticker: QQ: 你收到了x条新消息
        // text: 你收到了x条新消息

        /**
         * 匹配隐藏通知详情时的 Ticker.
         *
         * Group: 1新消息数目
         */
        private val hideMsgPattern: Pattern = Pattern.compile("^QQ: 你收到了(\\d+)条新消息$")

        // 群聊消息
        // ------------- 单个消息
        // title: 群名
        // ticker: 群名: [特别关心]昵称: 消息内容
        // text: [特别关心]昵称: 消息内容
        // ------------- 多个消息
        // title: 群名(x条新消息)
        // ticker: 群名(x条新消息): [特别关心]昵称: 消息内容
        // text: [特别关心]昵称: 消息内容
        // QQHD v5.8.8.3445 中群里特别关心前缀为 特别关注。

        /**
         * 匹配群聊消息 Ticker.
         *
         * 限制：昵称不能包含英文括号 `()`.
         */
        private val groupMsgPattern =
            """^(?<name>.+?)(?:\((?<num>\d+)条新消息\))?: (?<sp>\[特别关心])?(?<nickname>.+?): (?<msg>[\s\S]+)$""".toRegex()

        /**
         * 匹配群聊消息 Content.
         *
         * QQHD v5.8.8.3445 中群里特别关心前缀为 特别关注。
         */
        private val groupMsgContentPattern =
            """^(?<sp>\[特别关心])?(?<name>.+?): (?<msg>[\s\S]+)""".toRegex()

        // 私聊消息
        // title: [特别关心]昵称 | [特别关心]昵称(x条新消息)
        // ticker: [特别关心]昵称: 消息内容 | [特别关心]昵称(x条新消息): 消息内容
        // text: 消息内容

        /**
         * 匹配私聊消息 Ticker.
         *
         * Group: nickname-昵称, num-消息个数, msg-消息内容
         */
        private val msgPattern =
            """^(?<sp>\[特别关心])?(?<nickname>.+?)(\((?<num>\d+)条新消息\))?: (?<msg>[\s\S]+)$""".toRegex()

        // 关联QQ消息
        // title:
        //      - 只有一条消息: 关联QQ号
        //      - 一人发来多条消息: 关联QQ号 ({x}条新消息)
        //      - 多人发来消息: QQ
        // ticker:  关联QQ号-{发送者昵称}:{消息内容}
        // content:
        //      - 一人发来消息: {发送者昵称}:{消息内容}
        //      - 多人发来消息: 有 {x} 个联系人给你发过来{y}条新消息

        /**
         * 匹配关联 QQ 消息 ticker.
         *
         * Group: 1发送者昵称, 2消息内容
         */
        private val bindingQQMsgTickerPattern: Pattern =
            Pattern.compile("^关联QQ号-(.+?):([\\s\\S]+)$")

        /**
         * 匹配关联 QQ 消息 content. 用于提取未读消息个数。
         *
         * Group: 1未读消息个数
         */
        private val bindingQQMsgContextPattern: Pattern =
            Pattern.compile("^有 \\d+ 个联系人给你发过来(\\d+)条新消息$")

        /**
         * 匹配关联 QQ 消息 title. 用于提取未读消息个数。
         *
         * Group: 1未读消息个数
         */
        private val bindingQQMsgTitlePattern: Pattern =
            Pattern.compile("^关联QQ号 \\((\\d+)条新消息\\)$")
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
        if (isHidden(ticker)) {
            return QQNotification.HiddenMessage(tag)
        }
        tryResolveQZone(tag, title, content, ticker)?.also { return it }

        if (ticker == null) {
            Timber.tag(TAG).i("Ticker is null, skip")
            return null
        }

        tryResolveGroupMsg(tag, content, ticker)?.also { return it }
        tryResolvePrivateMsg(tag, content, ticker)?.also { return it }
        tryResolveBindingMsg(tag, title, content, ticker)?.also { return it }

        return null
    }

    private fun isHidden(ticker: String?): Boolean {
        return ticker != null && hideMsgPattern.matcher(ticker).matches()
    }

    private fun tryResolveQZone(
        tag: Tag,
        title: String,
        content: String,
        ticker: String?
    ): QQNotification? {
        if (ticker == null || !isQZone(title)) {
            return null
        }
        if (ticker.startsWith("【特别关心】")) {
            // 特别关心动态推送
            return QQNotification.QZoneSpecialPost(tag, content)
        }
        val num = matchQZoneNum(title)
        if (num != null) {
            // 普通空间通知
            return QQNotification.QZoneMessage(tag, content, num)
        }
        return null
    }

    private fun isQZone(title: String?): Boolean {
        return title?.let { qzoneTitlePattern.matcher(it).matches() } ?: false
    }

    /**
     * 提取空间未读消息个数。
     *
     * @return 动态未读消息个数。提取失败返回 `null`。
     */
    private fun matchQZoneNum(title: String): Int? {
        val matcher = qzoneTitlePattern.matcher(title)
        if (matcher.matches()) {
            return matcher.group(1)?.toIntOrNull()
        }
        return null
    }

    private fun tryResolveGroupMsg(tag: Tag, content: String, ticker: String): QQNotification? {
        if (content.isEmpty() || ticker.isEmpty()) {
            return null
        }
        val tickerGroups =
            groupMsgPattern.matchEntire(ticker)?.groups ?: return null
        val contentGroups =
            groupMsgContentPattern.matchEntire(content)?.groups ?: return null
        val name = tickerGroups["nickname"]?.value ?: return null
        val groupName = tickerGroups["name"]?.value ?: return null
        val num = tickerGroups["num"]?.value?.toIntOrNull()
        val text = contentGroups["msg"]?.value ?: return null
        val special = contentGroups["sp"]?.value != null

        return QQNotification.GroupMessage(
            tag = tag,
            groupName = groupName,
            nickname = name,
            message = text,
            special = special,
            num = num ?: 1,
        )
    }

    private fun tryResolvePrivateMsg(tag: Tag, content: String, ticker: String): QQNotification? {
        if (ticker.isEmpty() || content.isEmpty()) {
            return null
        }
        val tickerGroups = msgPattern.matchEntire(ticker)?.groups ?: return null
        val special = tickerGroups["sp"] != null
        val name = tickerGroups["nickname"]?.value ?: return null
        val num = tickerGroups["num"]?.value?.toIntOrNull()

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
        content: String,
        ticker: String
    ): QQNotification? {
        val matcher = bindingQQMsgTickerPattern.matcher(ticker)
        if (!matcher.matches()) {
            return null
        }

        val sender = matcher.group(1) ?: return null
        val text = matcher.group(2) ?: return null
        val num = matchBindingMsgNum(title, content)
        return QQNotification.BindingAccountMessage(tag, sender, text, num)
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
}