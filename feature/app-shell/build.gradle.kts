plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.feature.appshell.generated.resources"
    publicResClass = true
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.appshell"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(libs.compose.components.resources)
            api(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
