plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.feature.payment.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.payment"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:payment"))
            implementation(libs.bitcoin.kmp)
            implementation(libs.compose.runtime)
            api(libs.lightning.kmp.core)
            implementation(libs.uri.kmp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
