plugins {
    id("xyz.lilsus.raylsuite.kmp.library")
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.paymentcurrency"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:payment"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
