package cc.chenhe.qqnotifyevo.log

import timber.log.Timber

object CrashHandler : Thread.UncaughtExceptionHandler {

    private val default = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, t: Throwable) {
        try {
            Timber.e(t)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        default?.uncaughtException(thread, t)
    }

}