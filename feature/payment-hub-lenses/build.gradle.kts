plugins {
    id("xyz.lilsus.raylsuite.kmp.library")
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.paymenthub.lenses"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":feature:payment-hub"))
            implementation(project(":feature:payment-hub-lens-dock"))
            implementation(libs.multiplatform.settings)
        }
    }
}
