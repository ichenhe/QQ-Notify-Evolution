package cc.chenhe.qqnotifyevo.core

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.Test

class NotificationProcessorTest {

    private fun generateGroupTicker(nickName: String, groupName: String, message: String): String {
        return "$nickName($groupName):$message"
    }

    private fun generateFriendTicker(nickName: String, message: String): String {
        return "$nickName: $message"
    }

    private fun generateQzoneTitle(messageNum: Int = 1): String {
        return "QQ空间动态(共${messageNum}条未读)"
    }

    private fun generateHiddenTicker(messageNum: Int = 1): String {
        return "你收到了${messageNum}条新消息"
    }

    @Test
    fun group_ticker_match() {
        val ticker = generateGroupTicker("Bob", "Family", "Hello~")
        val matcher = NotificationProcessor.groupMsgPattern.matcher(ticker)
        matcher.matches().shouldBeTrue()

        matcher.group(1)!! shouldBeEqualTo "Bob"
        matcher.group(2)!! shouldBeEqualTo "Family"
        matcher.group(3)!! shouldBeEqualTo "Hello~"
    }

    @Test
    fun group_ticker_mismatch_friend() {
        val ticker = generateFriendTicker("Bob", "Hello~")
        val matcher = NotificationProcessor.groupMsgPattern.matcher(ticker)
        matcher.matches().shouldBeFalse()
    }

    @Test
    fun friend_ticker_match() {
        val ticker = generateFriendTicker("Alice", "hi")
        val matcher = NotificationProcessor.msgPattern.matcher(ticker)
        matcher.matches().shouldBeTrue()

        matcher.group(1)!! shouldBeEqualTo "Alice"
        matcher.group(2)!! shouldBeEqualTo "hi"
    }

    @Test
    fun friend_ticker_mismatch_group() {
        val ticker = generateGroupTicker("Alice", "group", "hi")
        val matcher = NotificationProcessor.msgPattern.matcher(ticker)
        matcher.matches().shouldBeFalse()
    }

    @Test
    fun qzone_title_match() {
        val title = generateQzoneTitle(2)
        val matcher = NotificationProcessor.qzonePattern.matcher(title)
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
        val title = "Bob (11条新消息)"
        val matcher = NotificationProcessor.msgTitlePattern.matcher(title)
        matcher.matches().shouldBeTrue()
        matcher.group(1)!!.toInt() shouldBeEqualTo 11
    }
}