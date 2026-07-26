plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.blip.ui.generated.resources"
    publicResClass = true
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.ui"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":blip:integration:blink"))
            implementation(project(":feature:payment"))
            implementation(libs.compose.components.resources)
            implementation(libs.compose.runtime)
        }
    }
}
