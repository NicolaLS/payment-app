plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.feature.payment.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.payment"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:payment"))
            implementation(project(":core:camera"))
            implementation(project(":core:ui"))
            implementation(project(":feature:contacts"))
            implementation(project(":feature:currency-settings"))
            implementation(project(":feature:payment-settings"))
            implementation(libs.bitcoin.kmp)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            api(libs.lightning.kmp.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.qrose)
            implementation(libs.uri.kmp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
