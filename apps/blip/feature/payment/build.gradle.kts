plugins {
    id("xyz.lilsus.raylsuite.kmp.compose")
}

compose.resources {
    packageOfResClass = "xyz.lilsus.blip.feature.payment.generated.resources"
}

kotlin {
    android {
        namespace = "xyz.lilsus.blip.feature.payment"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":blip:integration:blink"))
            api(project(":core:model"))
            api(project(":core:payment"))
            implementation(project(":core:ui"))
            implementation(project(":feature:contacts"))
            implementation(project(":feature:currency-settings"))
            implementation(project(":feature:payment-currency"))
            implementation(project(":feature:payment-settings"))
            api(project(":feature:payment-ui"))
            implementation(libs.bitcoin.kmp)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            api(libs.lightning.kmp.core)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.multiplatform.settings)
        }
        androidMain.dependencies {
            implementation(libs.secp256k1.kmp.jni.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
