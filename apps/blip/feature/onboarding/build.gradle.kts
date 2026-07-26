plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.blip.feature.onboarding.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.feature.onboarding"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":feature:onboarding"))
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
