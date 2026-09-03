plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.feature.themesettings.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.themesettings"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:settings"))
            implementation(project(":core:ui"))
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.multiplatform.settings)
        }
        androidMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
