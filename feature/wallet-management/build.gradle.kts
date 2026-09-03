plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources"
    publicResClass = true
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.walletmanagement"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(libs.compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
