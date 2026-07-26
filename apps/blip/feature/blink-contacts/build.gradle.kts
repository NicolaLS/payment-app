plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.blip.feature.blinkcontacts.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.feature.blinkcontacts"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":blip:integration:blink"))
            api(project(":blip:ui"))
            api(project(":feature:contacts"))
            implementation(project(":core:ui"))
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
