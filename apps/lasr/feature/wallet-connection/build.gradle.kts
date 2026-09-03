plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.mlkit.barcode.scanning)
        }
    }
}
