plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.ui"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":blip:integration:blink"))
            implementation(project(":core:ui"))
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(libs.compose.runtime)
        }
    }
}
