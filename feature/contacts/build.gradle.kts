plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.feature.contacts.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.raylsuite.feature.contacts"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(libs.compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
