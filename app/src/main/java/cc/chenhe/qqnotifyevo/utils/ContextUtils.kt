package cc.chenhe.qqnotifyevo.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * @throws IllegalStateException  can not find [Activity]
 */
fun Context.getActivity(): Activity {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    throw IllegalStateException("can not find Activity from current context")
}
