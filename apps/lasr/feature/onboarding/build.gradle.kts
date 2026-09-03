plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

compose.resources {
    packageOfResClass = "xyz.lilsus.lasr.feature.onboarding.generated.resources"
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
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.navigation.compose)
        }
    }
}
