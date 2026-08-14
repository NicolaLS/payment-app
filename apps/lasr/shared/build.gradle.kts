import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("xyz.lilsus.raylsuite.app.shared")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.lasr.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.lasr.shared"
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            binaryOption("bundleId", "xyz.lilsus.lasr.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":lasr:feature:onboarding"))
            implementation(project(":lasr:feature:payment"))
            implementation(project(":lasr:feature:wallet-connection"))
            implementation(project(":lasr:feature:wallet-details"))
            implementation(project(":lasr:integration:nwc"))
            implementation(project(":core:camera"))
            implementation(project(":core:network"))
            implementation(project(":core:settings"))
            implementation(project(":core:ui"))
            implementation(project(":feature:currency-settings"))
            implementation(project(":feature:contacts"))
            implementation(project(":feature:onboarding"))
            implementation(project(":feature:payment-settings"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:theme-settings"))
            implementation(project(":feature:wallet-management"))
            implementation(project(":integration:exchange-rate"))
            implementation(project(":integration:lnurl"))
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
