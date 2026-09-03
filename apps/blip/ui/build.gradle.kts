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
            implementation(libs.compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.compose.runtime)
        }
    }
}
