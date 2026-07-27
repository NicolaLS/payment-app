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
    packageOfResClass = "xyz.lilsus.raylsuite.lasr.generated.resources"
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "xyz.lilsus.lasr.shared"
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
            binaryOption("bundleId", "xyz.lilsus.lasr.shared")
            if (buildType == NativeBuildType.RELEASE) {
                freeCompilerArgs +=
                    "-Xdisable-phases=RemoveRedundantCallsToStaticInitializersPhase"
            }
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val iosMain by creating {
            dependsOn(commonMain)
        }
        val iosTest by creating {
            dependsOn(commonTest)
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosArm64Test by getting {
            dependsOn(iosTest)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest)
        }

        commonMain.dependencies {
            implementation(project(":lasr:feature:onboarding"))
            implementation(project(":lasr:feature:wallet-connection"))
            implementation(project(":lasr:feature:wallet-details"))
            implementation(project(":lasr:integration:nwc"))
            implementation(project(":lasr:ui"))
            implementation(project(":core:camera"))
            implementation(project(":core:network"))
            implementation(project(":core:settings"))
            implementation(project(":core:ui"))
            implementation(project(":feature:currency-settings"))
            implementation(project(":feature:contacts"))
            implementation(project(":feature:onboarding"))
            implementation(project(":feature:payment"))
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
