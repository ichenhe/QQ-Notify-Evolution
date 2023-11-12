package cc.chenhe.qqnotifyevo.core

import org.json.JSONObject

abstract class BaseResolverTest {
    protected data class NotificationData(
        val title: String?,
        val ticker: String?,
        val content: String?,
    )

    protected fun parse(json: String): NotificationData {
        val o = JSONObject(json)
        return NotificationData(
            title = o.getString("title"),
            content = o.getString("content"),
            ticker = o.getString("ticker")
        )
    }
}