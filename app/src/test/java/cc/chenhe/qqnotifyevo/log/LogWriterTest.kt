package cc.chenhe.qqnotifyevo.log

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.*


class LogWriterTest {

    companion object {
        private const val TIME = 1595582295000
    }

    private lateinit var cacheDir: File
    private lateinit var writer: LogWriter

    private fun createLogWriter(time: Long = System.currentTimeMillis()): LogWriter {
        cacheDir = File(System.getProperty("java.io.tmpdir"), "qqevo_log_test")
        return LogWriter(cacheDir, time)
    }

    @Before
    fun setup() {
        writer = createLogWriter()
        if (cacheDir.isDirectory) {
            cacheDir.deleteRecursively()
        }
    }

    @After
    fun after() {
        writer.close()
        cacheDir.deleteRecursively()
    }

    @Test
    fun writeLog() {
        writer.write("Test")
        writer.write("Hello")
        writer.logFile.readText() shouldBeEqualTo "Test\nHello\n"
    }

    @Test
    fun appendLog() {
        createLogWriter(TIME).use { w ->
            w.write("line1", TIME)
        }
        createLogWriter(TIME).use { w ->
            w.write("line2", TIME)
            w.logFile.readText() shouldBeEqualTo "line1\nline2\n"
        }
    }

    @Test
    fun writeLog_differentDay() {
        val calendar = Calendar.getInstance()
        writer.write("Test", calendar.timeInMillis)
        val f1 = writer.logFile

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        writer.write("Test2", calendar.timeInMillis)
        val f2 = writer.logFile

        f1.name shouldNotBeEqualTo f2.name
        f1.readText() shouldBeEqualTo "Test\n"
        f2.readText() shouldBeEqualTo "Test2\n"
    }
}