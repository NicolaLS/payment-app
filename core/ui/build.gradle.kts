plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.core.ui"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            api(libs.compose.material3)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.qrose)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.window)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
