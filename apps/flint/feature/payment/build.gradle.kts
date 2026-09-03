plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

kotlin {
    android {
        namespace = "xyz.lilsus.flint.feature.payment"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":flint:application"))
            api(project(":core:model"))
            api(project(":core:payment"))
            implementation(project(":core:ui"))
            api(project(":feature:payment-hub"))
            implementation(project(":feature:currency-settings"))
            implementation(project(":feature:payment-currency"))
            implementation(project(":feature:payment-settings"))
            api(project(":feature:payment-ui"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
