plugins {
    id("xyz.lilsus.raylsuite.kmp.library")
    alias(libs.plugins.breezSpark)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "xyz.lilsus.flint.integration.wallet"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":flint:application"))
            implementation(project(":core:payment"))
            implementation(libs.breez.sdk.spark)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("FlintDatabase") {
            packageName.set("xyz.lilsus.flint.integration.wallet.persistence")
            verifyMigrations.set(true)
        }
    }
}
