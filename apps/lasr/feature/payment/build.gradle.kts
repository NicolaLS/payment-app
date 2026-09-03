plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
    alias(libs.plugins.kotlinSerialization)
}

compose.resources {
    packageOfResClass = "xyz.lilsus.lasr.feature.payment.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.lasr.feature.payment"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:payment"))
            api(project(":lasr:integration:nwc"))
            implementation(project(":core:ui"))
            api(project(":feature:payment-hub"))
            implementation(project(":feature:currency-settings"))
            implementation(project(":feature:payment-currency"))
            implementation(project(":feature:payment-settings"))
            api(project(":feature:payment-ui"))
            implementation(libs.bitcoin.kmp)
            implementation(libs.compose.components.resources)
            api(libs.lightning.kmp.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.multiplatform.settings)
        }
        androidMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.secp256k1.kmp.jni.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
