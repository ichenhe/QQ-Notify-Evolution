import java.io.FileWriter

plugins {
    id("com.android.application") version "8.1.3" apply false

    // must correspond to compose compiler extension version:
    // https://developer.android.com/jetpack/androidx/releases/compose-kotlin#pre-release_kotlin_compatibility
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        @Suppress("DEPRECATION")
        //noinspection JcenterRepositoryObsolete
        jcenter {
            content {
                includeModule("com.oasisfeng.nevo", "sdk")
            }
        }
    }
}

tasks.register("appVersion") {
    description = "Calculate app version and save to version.properties"

    // execute on configuration phase
    val name = getVersionName()
    val code = 20000 + getVersionCode()
    logger.lifecycle("AppVersionName: {}\nAppVersionCode: {}", name, code)
    FileWriter(File(rootProject.projectDir, "version.properties")).use { fw ->
        fw.write("name=$name\ncode=$code\n")
        fw.flush()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

fun String.runCommand(currentWorkingDir: File = file("./")): String {
    val byteOut = java.io.ByteArrayOutputStream()
    project.exec {
        workingDir = currentWorkingDir
        commandLine = this@runCommand.split(" ")
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

fun getVersionCode(): Int {
    val cmd = "git rev-list HEAD --count"
    return try {
        cmd.runCommand().toInt()
    } catch (e: Exception) {
        logger.error("Failed to get version code with git, return 1 by default.", e)
        1
    }
}

fun getVersionName(): String {
    val cmd = "git describe --tags --long --abbrev=7 --dirty=_dev"
    try {
        val v = cmd.runCommand()
        val pattern = """^v(?<v>[\d|.]+)-\d+-g[A-Za-z0-9]{7}(?<s>_dev)?$""".toRegex()
        val g = pattern.matchEntire(v)?.groups
        if (g == null || g["v"] == null) {
            logger.error(
                "Failed to get version name with git.\n" +
                        "Cannot match git tag describe, return <UNKNOWN> by default. raw=$v"
            )
            return "UNKNOWN"
        }
        return g["v"]!!.value + (g["s"]?.value ?: "")
    } catch (e: Exception) {
        logger.error("Failed to get version name with git, return <UNKNOWN> by default.", e)
        return "UNKNOWN"
    }
}
