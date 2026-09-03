import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("xyz.lilsus.raylsuite.app.shared")
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.shared"
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            binaryOption("bundleId", "xyz.lilsus.blip.shared")
            export(project(":feature:payment-hub"))
            export(project(":feature:payment-ui"))
            export(project(":feature:settings"))
            export(project(":blip:feature:blink-contacts"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":blip:feature:onboarding"))
            implementation(project(":blip:feature:payment"))
            implementation(project(":blip:feature:wallet-connection"))
            api(project(":blip:feature:blink-contacts"))
            implementation(project(":blip:feature:wallet-settings"))
            implementation(project(":blip:integration:blink"))
            implementation(project(":blip:ui"))
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
            implementation(libs.multiplatform.settings)
            implementation(libs.compose.runtime)
        }
        androidMain.dependencies {
            implementation(project(":blip:feature:onboarding"))
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
