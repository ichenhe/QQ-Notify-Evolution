package cc.chenhe.qqnotifyevo.core

import cc.chenhe.qqnotifyevo.utils.PREFERENCE_CONVERSATION_BUBBLES_DEFAULT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationBubblePolicyTest {
    @Test fun `feature is opt in and Android 11 gated`() {
        assertFalse(PREFERENCE_CONVERSATION_BUBBLES_DEFAULT)
        assertFalse(ConversationBubblePolicy.isEnabled(29, true))
        assertFalse(ConversationBubblePolicy.isEnabled(30, false))
        assertTrue(ConversationBubblePolicy.isEnabled(30, true))
    }

    @Test fun `target must be exported permissionless and from exact source`() {
        assertTrue(ConversationBubblePolicy.isSafeTarget("com.tencent.mobileqq", "com.tencent.mobileqq", true, null))
        assertTrue(ConversationBubblePolicy.isSafeTarget("com.tencent.tim", "com.tencent.tim", true, ""))
        assertFalse(ConversationBubblePolicy.isSafeTarget("com.tencent.mobileqq", "com.tencent.tim", true, null))
        assertFalse(ConversationBubblePolicy.isSafeTarget("com.tencent.mobileqq", "com.tencent.mobileqq", false, null))
        assertFalse(ConversationBubblePolicy.isSafeTarget("com.tencent.mobileqq", "com.tencent.mobileqq", true, "private.permission"))
    }

    @Test fun `shortcut id is stable scoped and does not expose name`() {
        val first = ConversationBubblePolicy.shortcutId("com.tencent.mobileqq", "Alice")
        assertEquals(first, ConversationBubblePolicy.shortcutId("com.tencent.mobileqq", "Alice"))
        assertNotEquals(first, ConversationBubblePolicy.shortcutId("com.tencent.tim", "Alice"))
        assertFalse(first.contains("Alice"))
    }

    @Test fun `height and route states are explicit`() {
        assertEquals(512, ConversationBubblePolicy.DESIRED_HEIGHT_DP)
        assertEquals(BubbleRouteState.DISABLED, ConversationBubblePolicy.routeState(false, true, true))
        assertEquals(BubbleRouteState.LEARNING, ConversationBubblePolicy.routeState(true, false, false))
        assertEquals(BubbleRouteState.READY, ConversationBubblePolicy.routeState(true, true, true))
        assertEquals(BubbleRouteState.INVALID, ConversationBubblePolicy.routeState(true, true, false))
    }
}
