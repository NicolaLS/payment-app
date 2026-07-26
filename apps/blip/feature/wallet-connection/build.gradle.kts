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
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
