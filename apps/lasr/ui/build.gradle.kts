plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.lasr.ui.generated.resources"
    publicResClass = true
}

kotlin {
    android {
        namespace = "xyz.lilsus.lasr.ui"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":lasr:integration:nwc"))
            implementation(project(":feature:payment"))
            implementation(libs.compose.components.resources)
            implementation(libs.compose.runtime)
        }
    }
}
