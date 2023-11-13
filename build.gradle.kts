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
                includeModule("com.oasisfeng.nevo","sdk")
            }
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
