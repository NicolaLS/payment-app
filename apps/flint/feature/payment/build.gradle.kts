plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.flint.feature.payment.generated.resources"
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
            implementation(project(":feature:contacts"))
            implementation(project(":feature:currency-settings"))
            implementation(project(":feature:payment-settings"))
            api(project(":feature:payment-ui"))
            implementation(libs.compose.components.resources)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
