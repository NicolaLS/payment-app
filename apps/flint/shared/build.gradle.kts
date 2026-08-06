import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

compose.resources {
    packageOfResClass = "xyz.lilsus.raylsuite.flint.generated.resources"
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "xyz.lilsus.flint.shared"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }

        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "xyz.lilsus.flint.shared")
            if (buildType == NativeBuildType.RELEASE) {
                freeCompilerArgs +=
                    "-Xdisable-phases=RemoveRedundantCallsToStaticInitializersPhase"
            }
        }
    }

    sourceSets {
        val commonMain by getting
        val iosMain by creating {
            dependsOn(commonMain)
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }

        commonMain.dependencies {
            api(project(":flint:feature:payment"))
            api(project(":flint:feature:wallet-connection"))
            implementation(project(":flint:integration:spark"))
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
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}
