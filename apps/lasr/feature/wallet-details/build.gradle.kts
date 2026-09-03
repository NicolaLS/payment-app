plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

kotlin {
    android {
        namespace = "xyz.lilsus.lasr.feature.walletdetails"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":lasr:integration:nwc"))
            implementation(project(":core:ui"))
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
