plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.feature.walletconnection"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":blip:integration:blink"))
            api(project(":blip:ui"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(project(":core:ui"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
        iosMain.dependencies {
            implementation(project(":core:ui"))
        }
    }
}
