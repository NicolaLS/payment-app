import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("xyz.lilsus.raylsuite.app.shared")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.flint.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.flint.shared"
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            binaryOption("bundleId", "xyz.lilsus.flint.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":flint:application"))
            api(project(":flint:feature:payment"))
            api(project(":flint:feature:wallet-connection"))
            implementation(project(":flint:integration:wallet"))
            implementation(project(":core:camera"))
            implementation(project(":core:settings"))
            implementation(project(":core:ui"))
            implementation(project(":feature:contacts"))
            implementation(project(":feature:currency-settings"))
            implementation(project(":feature:onboarding"))
            implementation(project(":feature:payment-settings"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:theme-settings"))
            implementation(project(":feature:wallet-management"))
            implementation(project(":integration:exchange-rate"))
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.navigation.compose)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
        }
    }
}
