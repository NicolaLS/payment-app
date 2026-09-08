import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins { id("xyz.lilsus.raylsuite.app.shared") }

kotlin {
    android { namespace = "xyz.lilsus.blip.shared" }
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            export(project(":providers:blink:experience"))
            export(project(":feature:payment-hub"))
            export(project(":feature:payment-ui"))
            export(project(":feature:settings"))
            export(project(":providers:blink:feature:blink-contacts"))
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(project(":core:model"))
            api(project(":providers:blink:experience"))
            api(project(":feature:payment-hub"))
            api(project(":feature:payment-ui"))
            api(project(":feature:settings"))
            api(project(":providers:blink:feature:blink-contacts"))
            implementation(libs.compose.runtime)
        }
    }
}
