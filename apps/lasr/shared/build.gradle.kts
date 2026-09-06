import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins { id("xyz.lilsus.raylsuite.app.shared") }

kotlin {
    android { namespace = "xyz.lilsus.lasr.shared" }
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.withType<Framework>().configureEach {
            export(project(":providers:nwc:experience"))
            export(project(":feature:payment-hub"))
            export(project(":feature:payment-ui"))
            export(project(":feature:settings"))
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            api(project(":providers:nwc:experience"))
            api(project(":feature:payment-hub"))
            api(project(":feature:payment-ui"))
            api(project(":feature:settings"))
            implementation(libs.compose.runtime)
        }
    }
}
