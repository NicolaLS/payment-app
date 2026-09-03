plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.feature.onboarding"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(project(":blip:feature:blink-contacts"))
            implementation(project(":blip:feature:wallet-connection"))
            implementation(project(":blip:integration:blink"))
            implementation(project(":core:camera"))
            implementation(project(":core:ui"))
            implementation(project(":feature:payment-hub"))
            implementation(project(":feature:onboarding"))
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.navigation.compose)
        }
        iosMain.dependencies {
            implementation(project(":core:ui"))
        }
    }
}
