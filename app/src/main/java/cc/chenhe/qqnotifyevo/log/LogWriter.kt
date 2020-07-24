package cc.chenhe.qqnotifyevo.log

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class LogWriter(
        private val logDir: File,
        time: Long = System.currentTimeMillis()
) : AutoCloseable {

    companion object {
        private const val MILLIS_PER_DAY = 24 * 3600 * 1000
    }

    private var logFileTime: Long = time
    var logFile: File = logFile(logFileTime)
        private set(value) {
            field = value
            out.flush()
            out.close()
            out = FileOutputStream(value, true)
        }
    private var out: FileOutputStream = FileOutputStream(logFile, true)

    private fun logFile(time: Long): File {
        if (!logDir.isDirectory) {
            logDir.mkdirs()
        }
        val format = SimpleDateFormat("yyyyMMdd-HHmmssSSS", Locale.CHINA)
        return File(logDir, format.format(Date(time)) + ".log")
    }

    fun write(message: String, time: Long = System.currentTimeMillis()) {
        if (!isSameDay(logFileTime, time)) {
            logFileTime = time
            logFile = logFile(time)
        }
        out.write((message + "\n").toByteArray())
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        if (abs(t1 - t2) > MILLIS_PER_DAY) {
            return false
        }
        val offset = TimeZone.getDefault().rawOffset
        return (t1 + offset) / MILLIS_PER_DAY == (t2 + offset) / MILLIS_PER_DAY
    }

    override fun close() {
        out.close()
    }
}