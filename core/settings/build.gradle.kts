plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.core.settings"
    }

    sourceSets {
        commonMain.dependencies {
            // The Compose compiler plugin runs for this module's Android renderers.
            implementation(libs.compose.runtime)
            api(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.multiplatform.settings.test)
        }
        androidMain.dependencies {
            implementation(libs.compose.ui)
        }
    }
}
