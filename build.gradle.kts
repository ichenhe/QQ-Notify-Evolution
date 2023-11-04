plugins {
    id("com.android.application") version "8.1.2" apply false
    id("org.jetbrains.kotlin.android") version "1.6.21" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        @Suppress("DEPRECATION")
        jcenter()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
