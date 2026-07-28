plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.lasr.feature.walletconnection.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.lasr.feature.walletconnection"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":lasr:integration:nwc"))
            implementation(project(":core:camera"))
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
