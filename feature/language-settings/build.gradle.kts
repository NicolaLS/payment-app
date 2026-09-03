plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.feature.languagesettings.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.languagesettings"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:ui"))
            implementation(libs.compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
