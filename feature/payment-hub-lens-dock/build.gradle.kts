plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.feature.paymenthub.lens.dock.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.paymenthub.lens.dock"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":feature:payment-hub"))
            implementation(project(":core:ui"))
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
