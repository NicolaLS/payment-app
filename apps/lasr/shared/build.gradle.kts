import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("xyz.lilsus.raylsuite.app.shared")
}

kotlin {
    android {
        namespace = "xyz.lilsus.lasr.shared"
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            binaryOption("bundleId", "xyz.lilsus.lasr.shared")
            export(project(":feature:payment-hub"))
            export(project(":feature:payment-ui"))
            export(project(":feature:settings"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":lasr:feature:onboarding"))
            implementation(project(":lasr:feature:payment"))
            implementation(project(":lasr:integration:nwc"))
            implementation(project(":core:model"))
            implementation(project(":core:network"))
            implementation(project(":core:settings"))
            implementation(project(":core:ui"))
            implementation(project(":feature:currency-settings"))
            implementation(project(":feature:language-settings"))
            implementation(project(":feature:onboarding"))
            implementation(project(":feature:payment-settings"))
            api(project(":feature:payment-ui"))
            implementation(project(":feature:app-shell"))
            api(project(":feature:payment-hub"))
            api(project(":feature:settings"))
            implementation(project(":feature:theme-settings"))
            implementation(project(":integration:exchange-rate"))
            implementation(project(":integration:lnurl"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(project(":lasr:feature:wallet-details"))
            implementation(project(":feature:wallet-management"))
            implementation(libs.navigation.compose)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
        iosMain.dependencies {
            implementation(project(":core:camera"))
            implementation(project(":feature:wallet-management"))
            implementation(project(":lasr:feature:wallet-details"))
            implementation(project(":lasr:feature:wallet-connection"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
