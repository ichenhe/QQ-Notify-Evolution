package cc.chenhe.qqnotifyevo.core

import cc.chenhe.qqnotifyevo.utils.Tag
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeTypeOf
import org.junit.Before
import org.junit.Test

class TimNotificationResolverTest : BaseResolverTest() {
    private lateinit var resolver: TimNotificationResolver

    @Before
    fun setup() {
        resolver = TimNotificationResolver()
    }

    private fun resolve(data: NotificationData): QQNotification? {
        return resolver.resolveNotification(
            tag = Tag.TIM,
            title = data.title,
            content = data.content,
            ticker = data.ticker,
        )
    }

    // 私聊消息 -––––--––––---––––---––––---––––---––––---––––

    @Test
    fun private_normal() {
        val n = parse("""{"title":"咕咕咕","ticker":"咕咕咕: Hi","content":"Hi"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.PrivateMessage>()
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual(n.content!!)
        r.num.shouldBeEqual(1)
        r.special.shouldBeFalse()
    }

    @Test
    fun private_special() {
        val n =
            parse("""{"title":"[特别关心]咕咕咕","ticker":"咕咕咕: In memory of the days with another developer cs\nAnd I’m sorry ","content":"In memory of the days with another developer cs\nAnd I’m sorry "}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.PrivateMessage>()
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual(n.content!!)
        r.num.shouldBeEqual(1)
        r.special.shouldBeTrue()
    }

    @Test
    fun private_special_MultiMessage() {
        val n =
            parse("""{"title":"[特别关心]咕咕咕 (2条新消息)","ticker":"咕咕咕: &¥","content":"&¥"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.PrivateMessage>()
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual(n.content!!)
        r.num.shouldBeEqual(2)
        r.special.shouldBeTrue()
    }

    // 群聊消息 -––––--––––---––––---––––---––––---––––---––––

    @Test
    fun group_normal() {
        val n =
            parse("""{"title":"测试群","ticker":"咕咕咕(测试群):Xxx","content":"咕咕咕: Xxx"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.GroupMessage>()
        r.groupName.shouldBeEqual("测试群")
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual("Xxx")
        r.num.shouldBeEqual(1)
        r.special.shouldBeFalse()
    }

    @Test
    fun group_multiMessage() {
        val n =
            parse("""{"title":"测试群 (2条新消息)","ticker":"咕咕咕(测试群):Yyy","content":"咕咕咕: Yyy"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.GroupMessage>()
        r.groupName.shouldBeEqual("测试群")
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual("Yyy")
        r.num.shouldBeEqual(2)
        r.special.shouldBeFalse()
    }

    @Test
    fun group_special() {
        val n =
            parse("""{"title":"测试群","ticker":"咕咕咕(测试群):111","content":"[有关注的内容]咕咕咕: 111"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.GroupMessage>()
        r.groupName.shouldBeEqual("测试群")
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual("111")
        r.num.shouldBeEqual(1)
        r.special.shouldBeTrue()
    }

    @Test
    fun group_special_multiMessage() {
        val n =
            parse("""{"title":"测试群 (2条新消息)","ticker":"咕咕咕(测试群):222","content":"[有关注的内容]咕咕咕: 222"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.GroupMessage>()
        r.groupName.shouldBeEqual("测试群")
        r.nickname.shouldBeEqual("咕咕咕")
        r.message.shouldBeEqual("222")
        r.num.shouldBeEqual(2)
        r.special.shouldBeTrue()
    }

    // 其他 -––––--––––---––––---––––---––––---––––---––––

    @Test
    fun hidden() {
        val n =
            parse("""{"title":"TIM","ticker":"你收到了1条新消息","content":"你收到了1条新消息"}""")
        resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.HiddenMessage>()
    }


    @Test
    fun binding_multiMessage_multiLine() {
        val n =
            parse("""{"title":"关联QQ号 (2条新消息)","ticker":"关联QQ号-\/dev\/urandom:a\nb","content":"\/dev\/urandom:a\nb"}""")
        val r = resolve(n).shouldNotBeNull().shouldBeTypeOf<QQNotification.BindingAccountMessage>()
        r.sender.shouldBeEqual("/dev/urandom")
        r.message.shouldBeEqual("a\nb")
        r.num.shouldBeEqual(2)
    }
}