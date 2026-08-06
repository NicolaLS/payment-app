import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("xyz.lilsus.raylsuite.android.release")
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.isFile) file.inputStream().use(::load)
    }
val productionBreezApiKey =
    providers.gradleProperty("FLINT_BREEZ_API_KEY")
        .orElse(providers.environmentVariable("FLINT_BREEZ_API_KEY"))
        .orElse(localProperties.getProperty("FLINT_BREEZ_API_KEY") ?: "")

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

android {
    namespace = "xyz.lilsus.flint"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.lilsus.flint"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        manifestPlaceholders["appLabel"] = "Flint"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".dev"
            buildConfigField("String", "BREEZ_API_KEY", "".asBuildConfigString())
            manifestPlaceholders["appLabel"] = "Flint Dev"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField(
                "String",
                "BREEZ_API_KEY",
                productionBreezApiKey.get().asBuildConfigString()
            )
            manifestPlaceholders["appLabel"] = "Flint"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-rules.pro")
            )
        }
        create("e2e") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".e2e"
            matchingFallbacks += listOf("debug")
            manifestPlaceholders["appLabel"] = "Flint E2E"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":flint:shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.window)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
