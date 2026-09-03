plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.flint.feature.walletconnection.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.flint.feature.walletconnection"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":flint:application"))
            implementation(project(":core:ui"))
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
