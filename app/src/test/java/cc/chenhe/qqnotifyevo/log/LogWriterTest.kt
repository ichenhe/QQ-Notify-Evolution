package cc.chenhe.qqnotifyevo.log

import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.Calendar


class LogWriterTest {

    companion object {
        private const val TIME = 1595582295000

        private lateinit var cacheDir: File

        @BeforeClass
        @JvmStatic
        fun classSetup() {
            cacheDir = if (System.getenv("GITHUB_ACTIONS") == "true") {
                File(System.getenv("HOME"), "qqevo_log_test")
            } else {
                File(System.getProperty("java.io.tmpdir"), "qqevo_log_test")
            }
            println("[LogWriterTest] Cache dir: ${cacheDir.absolutePath}")
        }
    }

    private lateinit var writer: LogWriter

    private fun createLogWriter(): LogWriter {
        return LogWriter(cacheDir, TIME)
    }

    @Before
    fun setup() {
        if (cacheDir.isDirectory) {
            cacheDir.deleteRecursively()
        }
        writer = createLogWriter()
    }

    @After
    fun after() {
        writer.close()
        cacheDir.deleteRecursively()
    }

    @Test
    fun writeLog() {
        writer.write("Test", TIME)
        writer.write("Hello", TIME)
        writer.logFile.readText().shouldBeEqual("Test\nHello\n")
    }

    @Test
    fun appendLog() {
        createLogWriter().use { w ->
            w.write("line1", TIME)
        }
        createLogWriter().use { w ->
            w.write("line2", TIME)
            w.logFile.readText().shouldBeEqual("line1\nline2\n")
        }
    }

    @Test
    fun writeLog_differentDay() {
        val calendar = Calendar.getInstance().apply { timeInMillis = TIME }
        writer.write("Test", calendar.timeInMillis)
        val f1 = writer.logFile

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        writer.write("Test2", calendar.timeInMillis)
        val f2 = writer.logFile

        f1.name.shouldNotBeEqual(f2.name)
        f1.readText().shouldBeEqual("Test\n")
        f2.readText().shouldBeEqual("Test2\n")
    }
}