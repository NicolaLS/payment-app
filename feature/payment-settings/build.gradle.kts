plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.paymentsettings"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:payment"))
            implementation(project(":core:settings"))
            implementation(project(":core:ui"))
            implementation(project(":feature:contacts"))
            implementation(project(":feature:currency-settings"))
            implementation(project(":feature:payment-shortcuts"))
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
