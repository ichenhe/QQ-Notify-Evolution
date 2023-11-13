package cc.chenhe.qqnotifyevo.core

import cc.chenhe.qqnotifyevo.utils.Tag
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf
import org.junit.Before
import org.junit.Test

class QQNotificationResolverTest : BaseResolverTest() {
    private lateinit var resolver: QQNotificationResolver

    @Before
    fun setup() {
        resolver = QQNotificationResolver()
    }

    private fun resolve(data: NotificationData): QQNotification? {
        return resolver.resolveNotification(
            tag = Tag.QQ,
            title = data.title,
            content = data.content,
            ticker = data.ticker,
        )
    }

    // 私聊消息 -––––--––––---––––---––––---––––---––––---––––

    @Test
    fun private_normal() {
        val n = parse("""{"title":"咕咕咕","ticker":"咕咕咕: qqq","content":"123qqq"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.PrivateMessage>()
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual(n.content!!)
        r.num.shouldBeEqual(1)
        r.special.shouldBeFalse()
    }

    @Test
    fun private_special_MultiMessage() {
        val n =
            parse("""{"title":"[特别关心]咕咕咕(2条新消息)","ticker":"[特别关心]咕咕咕(2条新消息): 222","content":"222"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.PrivateMessage>()
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual(n.content!!)
        r.num.shouldBeEqual(2)
        r.special.shouldBeTrue()
    }

    @Test
    fun private_special() {
        val n =
            parse("""{"title":"[特别关心]咕咕咕","ticker":"[特别关心]咕咕咕: ok111","content":"ok111"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.PrivateMessage>()
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual(n.content!!)
        r.num.shouldBeEqual(1)
        r.special.shouldBeTrue()
    }

    // 群聊消息 -––––--––––---––––---––––---––––---––––---––––

    @Test
    fun group_normal() {
        val n =
            parse("""{"title":"测试群","ticker":"测试群: 咕咕咕: from group","content":"咕咕咕: from group"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.GroupMessage>()
        r.groupName.shouldBeEqual("测试群")
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual("from group")
        r.num.shouldBeEqual(1)
        r.special.shouldBeFalse()
    }

    @Test
    fun group_multiMessage() {
        val n =
            parse("""{"title":"测试群(2条新消息)","ticker":"测试群(2条新消息): 咕咕咕: 2222","content":"咕咕咕: 2222"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.GroupMessage>()
        r.groupName.shouldBeEqual("测试群")
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual("2222")
        r.num.shouldBeEqual(2)
        r.special.shouldBeFalse()
    }

    @Test
    fun group_special_multiMessage() {
        val n =
            parse("""{"title":"测试群(3条新消息)","ticker":"测试群(3条新消息): [特别关心]咕咕咕: 333","content":"[特别关心]咕咕咕: 333"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.GroupMessage>()
        r.groupName.shouldBeEqual("测试群")
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual("333")
        r.num.shouldBeEqual(3)
        r.special.shouldBeTrue()
    }

    @Test
    fun group_special() {
        val n =
            parse("""{"title":"测试群","ticker":"测试群: [特别关心]咕咕咕: from group","content":"[特别关心]咕咕咕: from group"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.GroupMessage>()
        r.groupName.shouldBeEqual("测试群")
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual("from group")
        r.num.shouldBeEqual(1)
        r.special.shouldBeTrue()
    }

    // QQ 空间 -––––--––––---––––---––––---––––---––––---––––

    @Test
    fun qzone_specialPost() {
        val n =
            parse("""{"title":"QQ空间动态","ticker":"【特别关心】咕咕咕：QZone post","content":"【特别关心】咕咕咕：QZone post"}""")
        resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.QZoneSpecialPost>()
    }

    @Test
    fun qzone_message() {
        val n =
            parse("""{"title":"QQ空间动态(共1条未读)","ticker":"咕咕咕赞了你的说说","content":"咕咕咕赞了你的说说"}""")
        resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.QZoneMessage>()
    }

    // 其他 -––––--––––---––––---––––---––––---––––---––––

    @Test
    fun hidden() {
        val n =
            parse(""" {"title":"QQ","ticker":"QQ: 你收到了1条新消息","content":"你收到了1条新消息"}""")
        resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.HiddenMessage>()
    }

    @Test
    fun binding_multiMessage_multiLine() {
        val n =
            parse("""{"title":"关联QQ号 (3条新消息)","ticker":"关联QQ号-\/dev\/urandom:d\nd","content":"\/dev\/urandom:d\nd"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.BindingAccountMessage>()
        r.sender.shouldBeEqual("/dev/urandom")
        r.message.shouldBeEqual("d\nd")
        r.num.shouldBeEqual(3)
    }
}