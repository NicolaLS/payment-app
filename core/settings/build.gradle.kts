plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.core.settings"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.multiplatform.settings)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui)
        }
    }
}
