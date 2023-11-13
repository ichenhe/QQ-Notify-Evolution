import org.jetbrains.kotlin.config.JvmTarget
import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The last code is 20001, make sure the result is greater than it.
val vCode = 20000 + getVersionCode()
val vName = getVersionName()
logger.lifecycle("App Version: $vName ($vCode)")

android {
    namespace = "cc.chenhe.qqnotifyevo"
    compileSdk = 34
    defaultConfig {
        applicationId = "cc.chenhe.qqnotifyevo"
        minSdk = 26
        targetSdk = 33
        versionCode = vCode
        versionName = vName
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
    composeOptions {
        // must correspond to kotlin version: https://developer.android.com/jetpack/androidx/releases/compose-kotlin#pre-release_kotlin_compatibility
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JvmTarget.JVM_1_8.description
    }
}

dependencies {
    val lifecycleVersion = "2.6.2"

    // compose
    val composeBom = platform("androidx.compose:compose-bom:2023.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.8.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")

    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0") // support ListenableFuture
    implementation("com.oasisfeng.nevo:sdk:2.0.0-rc01")
    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation("org.json:json:20231013") // JSONObject
}

fun String.runCommand(currentWorkingDir: File = file("./")): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        workingDir = currentWorkingDir
        commandLine = this@runCommand.split(" ")
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

fun getVersionCode(): Int {
    val cmd = "git rev-list HEAD --first-parent --count"
    return try {
        cmd.runCommand().toInt()
    } catch (e: Exception) {
        logger.error("Failed to get version code with git, return 1 by default.\n${e.message}")
        1
    }
}


fun getVersionName(): String {
    val cmd = "git describe --tags --long --first-parent --abbrev=7 --dirty=_dev"
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
        logger.error("Failed to get version name with git, return <UNKNOWN> by default.\n${e.message}")
        return "UNKNOWN"
    }
}
