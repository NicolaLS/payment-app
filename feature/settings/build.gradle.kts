plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.feature.settings.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.settings"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:payment"))
            implementation(project(":core:ui"))
            api(project(":feature:currency-settings"))
            api(project(":feature:language-settings"))
            api(project(":feature:payment-hub"))
            api(project(":feature:payment-settings"))
            api(project(":feature:theme-settings"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.navigation.event.compose)
        }
    }
}
