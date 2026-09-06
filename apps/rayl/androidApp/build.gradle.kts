plugins {
    id("xyz.lilsus.raylsuite.android.application")
}

if (file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "xyz.lilsus.rayl"

    defaultConfig {
        applicationId = "com.nicolasusca.rayl"
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["mainActivityName"] = "xyz.lilsus.rayl.MainActivity"
    }

    buildTypes {
        getByName("debug") {
            manifestPlaceholders["appLabel"] = "Rayl Dev"
        }
        getByName("release") {
            manifestPlaceholders["appLabel"] = "Rayl"
        }
        create("e2e") {
            initWith(getByName("release"))
            applicationIdSuffix = ".e2e"
            matchingFallbacks += listOf("release")
            manifestPlaceholders["appLabel"] = "Rayl E2E"
            manifestPlaceholders["mainActivityName"] = "xyz.lilsus.rayl.E2eMainActivity"
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
    implementation(project(":rayl:shared"))
    implementation(project(":core:network"))
    implementation(project(":core:ui"))
    implementation(project(":integration:performance-monitoring"))
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.core.ktx)
}
