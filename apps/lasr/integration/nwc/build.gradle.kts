plugins {
    id("xyz.lilsus.raylsuite.kmp.library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "xyz.lilsus.lasr.integration.nwc"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:payment"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
        }
    }
}
