plugins {
    id("xyz.lilsus.raylsuite.kmp.library")
    alias(libs.plugins.breezSpark)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "xyz.lilsus.flint.integration.spark"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":flint:feature:payment"))
            implementation(project(":flint:feature:wallet-connection"))
            implementation(libs.breez.sdk.spark)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
        }
    }
}

sqldelight {
    databases {
        create("FlintDatabase") {
            packageName.set("xyz.lilsus.flint.database")
            verifyMigrations.set(true)
        }
    }
}
