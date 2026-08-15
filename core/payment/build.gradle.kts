plugins {
    id("xyz.lilsus.raylsuite.kmp.library")
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.core.payment"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(libs.bitcoin.kmp)
            api(libs.lightning.kmp.core)
            implementation(libs.uri.kmp)
        }
    }
}
