plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "xyz.lilsus.lasr.feature.onboarding"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:camera"))
            implementation(project(":core:ui"))
            implementation(project(":feature:onboarding"))
            implementation(project(":lasr:feature:wallet-connection"))
            implementation(project(":lasr:integration:nwc"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.navigation.compose)
        }
    }
}
