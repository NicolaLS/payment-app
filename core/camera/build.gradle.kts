plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.core.camera"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}
