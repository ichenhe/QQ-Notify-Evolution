package cc.chenhe.qqnotifyevo.core

import org.amshove.kluent.*
import org.junit.Test

class NotificationProcessorTest {

    private fun generateGroupTicker(
        nickName: String,
        groupName: String,
        message: String,
        special: Boolean = false,
        messageNum: Int = 1,
    ): String {
        val sb = StringBuilder(groupName)
        if (messageNum > 1)
            sb.append("(${messageNum}条新消息)")
        sb.append(": ")
        if (special)
            sb.append("[特别关心]")
        sb.append(nickName).append(": ").append(message)
        return sb.toString()
    }

    private fun generateGroupContent(nickName: String, message: String, special: Boolean): String {
        return "${"[特别关心]".takeIf { special }.orEmpty()}$nickName: $message"
    }

    private fun generateFriendTicker(nickName: String, message: String): String {
        return "$nickName: $message"
    }

    private fun generateFriendTitle(
        nickName: String,
        messageNum: Int,
        special: Boolean = false
    ): String {
        return (if (special) "[特别关心]" else "") + nickName + if (messageNum > 1) "(${messageNum}条新消息)" else ""
    }

    private fun generateQzoneTitle(messageNum: Int = 1): String {
        return "QQ空间动态(共${messageNum}条未读)"
    }

    private fun generateHiddenTicker(messageNum: Int = 1): String {
        return "QQ: 你收到了${messageNum}条新消息"
    }

    @Test
    fun group_ticker_match() {
        val ticker = generateGroupTicker("Bob", "Family(1)", "Hello~")
        val groups = NotificationProcessor.groupMsgPattern.matchEntire(ticker)?.groups
        groups.shouldNotBeNull()

        groups["sp"].shouldBeNull()
        groups["num"].shouldBeNull()
        groups["nickname"]?.value shouldBeEqualTo "Bob"
        groups["name"]?.value shouldBeEqualTo "Family(1)"
        groups["msg"]?.value shouldBeEqualTo "Hello~"
    }

    @Test
    fun group_ticker_multiMsg_match() {
        val ticker = generateGroupTicker("Bob", "Family(1)", "Hello~", messageNum = 3)
        val groups = NotificationProcessor.groupMsgPattern.matchEntire(ticker)?.groups
        groups.shouldNotBeNull()

        groups["sp"].shouldBeNull()
        groups["num"]?.value?.toIntOrNull() shouldBeEqualTo 3
        groups["nickname"]?.value shouldBeEqualTo "Bob"
        groups["name"]?.value shouldBeEqualTo "Family(1)"
        groups["msg"]?.value shouldBeEqualTo "Hello~"
    }

    @Test
    fun group_ticker_match_multiLines() {
        val ticker = generateGroupTicker("Bob", "Family(1)", "Hello\nhere\nyep")
        val groups = NotificationProcessor.groupMsgPattern.matchEntire(ticker)?.groups
        groups.shouldNotBeNull()

        groups["sp"].shouldBeNull()
        groups["num"].shouldBeNull()
        groups["nickname"]?.value shouldBeEqualTo "Bob"
        groups["name"]?.value shouldBeEqualTo "Family(1)"
        groups["msg"]?.value shouldBeEqualTo "Hello\nhere\nyep"
    }

    @Test
    fun group_ticker_special_match() {
        val ticker = generateGroupTicker("Bob", "Family (1)", "Hello", special = true)
        val groups = NotificationProcessor.groupMsgPattern.matchEntire(ticker)?.groups
        groups.shouldNotBeNull()

        groups["sp"].shouldNotBeNull()
        groups["num"].shouldBeNull()
        groups["name"]?.value shouldBeEqualTo "Family (1)"
        groups["nickname"]?.value shouldBeEqualTo "Bob"
        groups["msg"]?.value shouldBeEqualTo "Hello"
    }

    @Test
    fun group_ticker_mismatch_friend() {
        val ticker = generateFriendTicker("Bob", "Hello~")
        val groups = NotificationProcessor.groupMsgPattern.matchEntire(ticker)?.groups
        groups.shouldBeNull()
    }

    @Test
    fun group_content_special_match() {
        val content = generateGroupContent("(id1)", "Yea", true)
        val groups = NotificationProcessor.groupMsgContentPattern.matchEntire(content)?.groups
        groups.shouldNotBeNull()

        groups["sp"]?.value.shouldNotBeNullOrEmpty()
        groups["name"]?.value shouldBeEqualTo "(id1)"
        groups["msg"]?.value shouldBeEqualTo "Yea"
    }

    @Test
    fun group_content_nonSpecial_match() {
        val content = generateGroupContent("(id2)", "Yes", false)
        val groups = NotificationProcessor.groupMsgContentPattern.matchEntire(content)?.groups
        groups.shouldNotBeNull()

        groups["sp"].shouldBeNull()
        groups["name"]?.value shouldBeEqualTo "(id2)"
        groups["msg"]?.value shouldBeEqualTo "Yes"
    }

    @Test
    fun friend_ticker_match() {
        val ticker = generateFriendTicker("Alice", "hi")
        val groups = NotificationProcessor.msgPattern.matchEntire(ticker)?.groups
        groups.shouldNotBeNull()

        groups["nickname"]?.value shouldBeEqualTo "Alice"
        groups["msg"]?.value shouldBeEqualTo "hi"
    }

    @Test
    fun friend_ticker_match_multiLines() {
        val ticker = generateFriendTicker("Alice", "hi\nok\nthanks")
        val groups = NotificationProcessor.msgPattern.matchEntire(ticker)?.groups
        groups.shouldNotBeNull()

        groups["nickname"]?.value shouldBeEqualTo "Alice"
        groups["msg"]?.value shouldBeEqualTo "hi\nok\nthanks"
    }

    @Test
    fun friend_title_match_single() {
        val title = generateFriendTitle("Bob", 1, false)
        val matcher = NotificationProcessor.msgTitlePattern.matcher(title)
        matcher.matches().shouldBeTrue()

        matcher.group(1).shouldBeNull()
        matcher.group(2).shouldBeNull()
    }

    @Test
    fun friend_special_title_match_single() {
        val title = generateFriendTitle("Bob", 1, true)
        val matcher = NotificationProcessor.msgTitlePattern.matcher(title)
        matcher.matches().shouldBeTrue()

        matcher.group(1).shouldNotBeNull()
        matcher.group(2).shouldBeNull()
    }

    @Test
    fun friend_title_match_multi() {
        val title = generateFriendTitle("Bob", 11, false)
        val matcher = NotificationProcessor.msgTitlePattern.matcher(title)
        matcher.matches().shouldBeTrue()

        matcher.group(1).shouldBeNull()
        matcher.group(2)!!.toInt() shouldBeEqualTo 11
    }

    @Test
    fun friend_special_title_match_multi() {
        val title = generateFriendTitle("Bob", 11, true)
        val matcher = NotificationProcessor.msgTitlePattern.matcher(title)
        matcher.matches().shouldBeTrue()

        matcher.group(1).shouldNotBeNull()
        matcher.group(2)?.toIntOrNull() shouldBeEqualTo 11
    }

    @Test
    fun qzone_title_match() {
        val title = generateQzoneTitle(2)
        val matcher = NotificationProcessor.qzoneTitlePattern.matcher(title)
        matcher.matches().shouldBeTrue()
        matcher.group(1)!!.toInt() shouldBeEqualTo 2
    }

    @Test
    fun hidden_message_match() {
        val ticker = generateHiddenTicker()
        val matcher = NotificationProcessor.hideMsgPattern.matcher(ticker)
        matcher.matches().shouldBeTrue()
    }

    @Test
    fun hidden_message_mismatch_friend() {
        val ticker = generateFriendTicker("Bob", "Hello~")
        val matcher = NotificationProcessor.hideMsgPattern.matcher(ticker)
        matcher.matches().shouldBeFalse()
    }

    @Test
    fun hidden_message_mismatch_group() {
        val ticker = generateGroupTicker("Alice", "group", "hi")
        val matcher = NotificationProcessor.hideMsgPattern.matcher(ticker)
        matcher.matches().shouldBeFalse()
    }

    @Test
    fun chat_message_num_match() {
        val title = "Bob (2条新消息)"
        val matcher = NotificationProcessor.msgTitlePattern.matcher(title)
        matcher.matches().shouldBeTrue()
        matcher.group(2)!!.toInt() shouldBeEqualTo 2
    }

    @Test
    fun chat_message_num_mismatch() {
        val title = generateFriendTitle("Bob", 1)
        val matcher = NotificationProcessor.msgTitlePattern.matcher(title)
        matcher.matches().shouldBeTrue()
        matcher.group(2).shouldBeNull()
    }
}