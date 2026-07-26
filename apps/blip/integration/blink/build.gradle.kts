plugins {
    id("xyz.lilsus.raylsuite.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.integration.blink"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:payment"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
