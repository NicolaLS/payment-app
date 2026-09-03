plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

compose.resources {
    packageOfResClass = "xyz.lilsus.blip.feature.onboarding.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.feature.onboarding"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":blip:feature:blink-contacts"))
            implementation(project(":blip:feature:wallet-connection"))
            implementation(project(":blip:integration:blink"))
            implementation(project(":core:camera"))
            implementation(project(":core:ui"))
            implementation(project(":feature:payment-hub"))
            implementation(project(":feature:onboarding"))
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.navigation.compose)
        }
    }
}
