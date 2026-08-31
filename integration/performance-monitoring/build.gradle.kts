plugins {
    id("com.android.library")
    alias(libs.plugins.ktlint)
}

android {
    namespace = "xyz.lilsus.raylsuite.integration.performancemonitoring"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.performance)
    implementation(project(":core:camera"))
    api(project(":feature:settings"))
}
