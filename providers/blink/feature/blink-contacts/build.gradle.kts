plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.feature.blinkcontacts"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":providers:blink:integration:blink"))
            api(project(":providers:blink:ui"))
            api(project(":core:model"))
            api(project(":feature:payment-hub"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(project(":core:ui"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
        iosMain.dependencies {
            implementation(project(":core:ui"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
