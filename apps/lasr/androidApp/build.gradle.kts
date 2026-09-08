plugins {
    id("xyz.lilsus.raylsuite.android.application")
}

android {
    namespace = "xyz.lilsus.lasr"

    defaultConfig {
        applicationId = "xyz.lilsus.lasr"
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["mainActivityName"] = "xyz.lilsus.lasr.MainActivity"
    }

    buildTypes {
        getByName("debug") {
            manifestPlaceholders["appLabel"] = "Lasr Dev"
        }
        getByName("release") {
            manifestPlaceholders["appLabel"] = "Lasr"
        }
        create("e2e") {
            initWith(getByName("release"))
            applicationIdSuffix = ".e2e"
            matchingFallbacks += listOf("release")
            manifestPlaceholders["appLabel"] = "Lasr E2E"
            manifestPlaceholders["mainActivityName"] = "xyz.lilsus.lasr.E2eMainActivity"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        jniLibs {
            excludes += setOf(
                "/lib/armeabi/libjnidispatch.so",
                "/lib/mips/libjnidispatch.so",
                "/lib/mips64/libjnidispatch.so"
            )
        }
        resources {
            excludes += "/fr/acinq/secp256k1/jni/native/**"
        }
    }
}

dependencies {
    implementation(project(":lasr:shared"))
    implementation(project(":core:network"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.core.ktx)
}
