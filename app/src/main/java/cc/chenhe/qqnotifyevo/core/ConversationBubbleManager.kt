package cc.chenhe.qqnotifyevo.core

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.pm.ShortcutManagerCompat
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal enum class BubbleRouteState { DISABLED, LEARNING, READY, INVALID }

internal object ConversationBubblePolicy {
    const val DESIRED_HEIGHT_DP = 512
    const val SHORTCUT_PREFIX = "conversation-"
    const val LEARN_INTERVAL_MS = 30_000L

    fun isEnabled(apiLevel: Int, preference: Boolean): Boolean =
        apiLevel >= Build.VERSION_CODES.R && preference

    fun routeState(enabled: Boolean, hasRoute: Boolean, routeValid: Boolean): BubbleRouteState =
        when {
            !enabled -> BubbleRouteState.DISABLED
            !hasRoute -> BubbleRouteState.LEARNING
            routeValid -> BubbleRouteState.READY
            else -> BubbleRouteState.INVALID
        }

    fun isSafeTarget(
        expectedPackage: String,
        componentPackage: String?,
        exported: Boolean,
        permission: String?,
    ): Boolean = componentPackage == expectedPackage && exported && permission.isNullOrEmpty()

    fun shortcutId(sourcePackage: String, conversationName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$sourcePackage\u0000$conversationName".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return SHORTCUT_PREFIX + digest.take(32)
    }
}

/**
 * Learns QQ/TIM's direct Activity intent from PendingIntent.OnFinished. Routes and callback
 * extras stay in process memory: they are never logged, persisted or uploaded.
 */
internal class ConversationBubbleManager(private val context: Context) {
    private data class Route(val sourcePackage: String, val intent: Intent)

    private val routes = ConcurrentHashMap<String, Route>()
    private val attempted = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var enabled = false
    @Volatile private var lastLearnAt = 0L

    fun setEnabled(value: Boolean) {
        val next = ConversationBubblePolicy.isEnabled(Build.VERSION.SDK_INT, value)
        if (enabled && !next) {
            routes.clear()
            attempted.clear()
            val ids = ShortcutManagerCompat.getDynamicShortcuts(context)
                .map { it.id }
                .filter { it.startsWith(ConversationBubblePolicy.SHORTCUT_PREFIX) }
            if (ids.isNotEmpty()) {
                ShortcutManagerCompat.removeDynamicShortcuts(context, ids)
                ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
            }
        }
        enabled = next
    }

    fun maybeLearn(shortcutId: String, sourcePackage: String, contentIntent: PendingIntent?) {
        if (!enabled || contentIntent == null || routes.containsKey(shortcutId) || attempted.contains(shortcutId)) return
        val now = System.currentTimeMillis()
        if (now - lastLearnAt < ConversationBubblePolicy.LEARN_INTERVAL_MS) return
        attempted.add(shortcutId)
        lastLearnAt = now
        try {
            contentIntent.send(
                context,
                0,
                null,
                { _, returnedIntent, _, _, _ ->
                    val safe = returnedIntent?.let { validateAndCopy(it, sourcePackage) }
                    if (safe != null && enabled) routes[shortcutId] = Route(sourcePackage, safe)
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: PendingIntent.CanceledException) {
            // Deliberately silent: callback data and route details must not enter logs.
        } catch (_: SecurityException) {
            // Deliberately silent; the notification remains a normal conversation notification.
        }
    }

    fun route(shortcutId: String, sourcePackage: String): Intent? {
        if (!enabled) return null
        val route = routes[shortcutId] ?: return null
        if (route.sourcePackage != sourcePackage) return null
        return validateAndCopy(route.intent, sourcePackage)
    }

    private fun validateAndCopy(intent: Intent, sourcePackage: String): Intent? {
        val component = intent.component ?: return null
        if (component.packageName != sourcePackage) return null
        val info = try {
            context.packageManager.getActivityInfo(component, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        if (!ConversationBubblePolicy.isSafeTarget(
                sourcePackage, info.packageName, info.exported, info.permission
            )) return null
        return Intent(intent).setComponent(ComponentName(info.packageName, info.name))
    }
}
