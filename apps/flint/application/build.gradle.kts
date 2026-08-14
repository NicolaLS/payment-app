plugins {
    id("xyz.lilsus.raylsuite.kmp.library")
}

kotlin {
    android {
        namespace = "xyz.lilsus.flint.application"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:payment"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
        }
    }
}
