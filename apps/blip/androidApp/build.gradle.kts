import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val uploadStoreFile = providers.environmentVariable("RAYL_UPLOAD_STORE_FILE")
val uploadStorePassword = providers.environmentVariable("RAYL_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = providers.environmentVariable("RAYL_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = providers.environmentVariable("RAYL_UPLOAD_KEY_PASSWORD")
val uploadSigningConfigured =
    listOf(uploadStoreFile, uploadStorePassword, uploadKeyAlias, uploadKeyPassword)
        .all { it.isPresent }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

android {
    namespace = "xyz.lilsus.blip"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.lilsus.blip"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["mainActivityName"] = "xyz.lilsus.blip.MainActivity"
    }

    signingConfigs {
        if (uploadSigningConfigured) {
            create("upload") {
                storeFile = file(uploadStoreFile.get())
                storePassword = uploadStorePassword.get()
                keyAlias = uploadKeyAlias.get()
                keyPassword = uploadKeyPassword.get()
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appLabel"] = "Blip Dev"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appLabel"] = "Blip"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-rules.pro")
            )
            if (uploadSigningConfigured) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
        create("e2e") {
            initWith(getByName("release"))
            applicationIdSuffix = ".e2e"
            matchingFallbacks += listOf("release")
            manifestPlaceholders["appLabel"] = "Blip E2E"
            manifestPlaceholders["mainActivityName"] = "xyz.lilsus.blip.E2eMainActivity"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    androidResources {
        generateLocaleConfig = true
    }
    packaging {
        resources {
            excludes += "/fr/acinq/secp256k1/jni/native/**"
        }
    }
}

dependencies {
    implementation(project(":blip:shared"))
    implementation(project(":core:network"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.window)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
