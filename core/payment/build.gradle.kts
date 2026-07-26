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
        }
    }
}
