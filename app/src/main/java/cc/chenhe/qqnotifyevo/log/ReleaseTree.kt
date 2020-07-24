package cc.chenhe.qqnotifyevo.log

import android.util.Log
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ReleaseTree(logDir: File) : Timber.Tree() {

    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA)
    private val date = Date()
    private val logWriter = LogWriter(logDir)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val p = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "U"
        }
        date.time = System.currentTimeMillis()
        val time = format.format(date)

        var s = "$time [$p] [${tag}]: $message"
        t?.let {
            s += "\n${it.message}"
        }
        logWriter.write(s, date.time)
    }

}