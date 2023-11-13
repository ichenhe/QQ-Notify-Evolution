import org.jetbrains.kotlin.config.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionProperties = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.isFile) {
        load(f.reader())
    }
}

android {
    namespace = "cc.chenhe.qqnotifyevo"
    compileSdk = 34
    defaultConfig {
        applicationId = "cc.chenhe.qqnotifyevo"
        minSdk = 26
        targetSdk = 33
        versionCode = versionProperties.getProperty("code", "1").toIntOrNull() ?: 1
        versionName = versionProperties.getProperty("name", "UNKNOWN")
    }
    buildFeatures {
        viewBinding = true
        compose = true
    }
    composeOptions {
        // must correspond to kotlin version: https://developer.android.com/jetpack/androidx/releases/compose-kotlin#pre-release_kotlin_compatibility
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    signingConfigs {
        readSigningConfig()?.also { config ->
            logger.lifecycle("Use key alias '{}' to sign release", config.keyAlias)
            create("release") {
                storeFile = config.storeFile
                storePassword = "chenhe"
                keyAlias = "weargallery"
                keyPassword = "chenhe"

                enableV2Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.also { signingConfig = it }
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

data class SigningConfig(
    val storeFile: File,
    val storePwd: String,
    val keyAlias: String,
    val keyPwd: String,
)

fun readSigningConfig(): SigningConfig? {
    val path = System.getenv("QNEVO_SIGNING_STORE_PATH") ?: return null
    val f = File(path)
    if (!f.isFile) {
        logger.warn("Key store file not exist: {}", path)
        return null
    }
    return SigningConfig(
        storeFile = f,
        storePwd = System.getenv("QNEVO_SIGNING_STORE_PWD") ?: return null,
        keyAlias = System.getenv("QNEVO_SIGNING_KEY_ALIAS") ?: return null,
        keyPwd = System.getenv("QNEVO_SIGNING_KEY_PWD") ?: return null
    )
}
