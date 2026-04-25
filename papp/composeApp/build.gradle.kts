import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val releaseSigningPropertiesFile = providers.gradleProperty("papp.release.signing.properties")
    .orElse(providers.environmentVariable("PAPP_ANDROID_SIGNING_PROPERTIES"))
    .map { file(it) }
    .orNull
    ?: file("/Users/sus/scratch/android-release-papp/signing.properties").takeIf { it.isFile }

val releaseSigningProperties = Properties().apply {
    releaseSigningPropertiesFile?.inputStream()?.use(::load)
}

fun releaseSigningValue(propertyName: String, environmentName: String): String? =
    providers.gradleProperty(propertyName).orNull
        ?: providers.environmentVariable(environmentName).orNull
        ?: releaseSigningProperties.getProperty(propertyName)

fun releaseSigningPassword(
    propertyName: String,
    environmentName: String,
    filePropertyName: String,
    fileEnvironmentName: String
): String? {
    val passwordFile = releaseSigningValue(filePropertyName, fileEnvironmentName)
        ?.let { file(it) }
        ?.takeIf { it.isFile }

    return releaseSigningValue(propertyName, environmentName)
        ?: passwordFile?.readLines()?.firstOrNull()
}

fun releaseSigningPasswordArgument(
    password: String?,
    filePropertyName: String,
    fileEnvironmentName: String
): String? {
    val passwordFile = releaseSigningValue(filePropertyName, fileEnvironmentName)
        ?.let { file(it) }
        ?.takeIf { it.isFile }

    return when {
        passwordFile != null -> "file:${passwordFile.path}"
        password != null -> "pass:$password"
        else -> null
    }
}

val bundleStoreFilePath = releaseSigningValue(
    propertyName = "papp.release.bundle.store.file",
    environmentName = "PAPP_RELEASE_BUNDLE_STORE_FILE"
) ?: releaseSigningValue(
    propertyName = "papp.release.store.file",
    environmentName = "PAPP_RELEASE_STORE_FILE"
) ?: "/Users/sus/scratch/android-release-papp/publish-key/papp-publish.jks"
val bundleStoreFile = file(bundleStoreFilePath)
val bundleStorePassword = releaseSigningPassword(
    propertyName = "papp.release.bundle.store.password",
    environmentName = "PAPP_RELEASE_BUNDLE_STORE_PASSWORD",
    filePropertyName = "papp.release.bundle.store.password.file",
    fileEnvironmentName = "PAPP_RELEASE_BUNDLE_STORE_PASSWORD_FILE"
) ?: releaseSigningPassword(
    propertyName = "papp.release.store.password",
    environmentName = "PAPP_RELEASE_STORE_PASSWORD",
    filePropertyName = "papp.release.store.password.file",
    fileEnvironmentName = "PAPP_RELEASE_STORE_PASSWORD_FILE"
)
val bundleKeyAlias = releaseSigningValue(
    propertyName = "papp.release.bundle.key.alias",
    environmentName = "PAPP_RELEASE_BUNDLE_KEY_ALIAS"
) ?: releaseSigningValue(
    propertyName = "papp.release.key.alias",
    environmentName = "PAPP_RELEASE_KEY_ALIAS"
)
val bundleKeyPassword = releaseSigningPassword(
    propertyName = "papp.release.bundle.key.password",
    environmentName = "PAPP_RELEASE_BUNDLE_KEY_PASSWORD",
    filePropertyName = "papp.release.bundle.key.password.file",
    fileEnvironmentName = "PAPP_RELEASE_BUNDLE_KEY_PASSWORD_FILE"
) ?: releaseSigningPassword(
    propertyName = "papp.release.key.password",
    environmentName = "PAPP_RELEASE_KEY_PASSWORD",
    filePropertyName = "papp.release.key.password.file",
    fileEnvironmentName = "PAPP_RELEASE_KEY_PASSWORD_FILE"
) ?: bundleStorePassword

val apkStoreFilePath = releaseSigningValue(
    propertyName = "papp.release.apk.store.file",
    environmentName = "PAPP_RELEASE_APK_STORE_FILE"
) ?: "/Users/sus/scratch/android-release-papp/release-key/papp-signing.jks"
val apkStoreFile = file(apkStoreFilePath)
val apkStorePassword = releaseSigningPassword(
    propertyName = "papp.release.apk.store.password",
    environmentName = "PAPP_RELEASE_APK_STORE_PASSWORD",
    filePropertyName = "papp.release.apk.store.password.file",
    fileEnvironmentName = "PAPP_RELEASE_APK_STORE_PASSWORD_FILE"
) ?: bundleStorePassword
val apkKeyAlias = releaseSigningValue(
    propertyName = "papp.release.apk.key.alias",
    environmentName = "PAPP_RELEASE_APK_KEY_ALIAS"
) ?: bundleKeyAlias
val apkKeyPassword = releaseSigningPassword(
    propertyName = "papp.release.apk.key.password",
    environmentName = "PAPP_RELEASE_APK_KEY_PASSWORD",
    filePropertyName = "papp.release.apk.key.password.file",
    fileEnvironmentName = "PAPP_RELEASE_APK_KEY_PASSWORD_FILE"
) ?: apkStorePassword

val apkStorePasswordArgument = releaseSigningPasswordArgument(
    password = apkStorePassword,
    filePropertyName = "papp.release.apk.store.password.file",
    fileEnvironmentName = "PAPP_RELEASE_APK_STORE_PASSWORD_FILE"
)
val apkKeyPasswordArgument = releaseSigningPasswordArgument(
    password = apkKeyPassword,
    filePropertyName = "papp.release.apk.key.password.file",
    fileEnvironmentName = "PAPP_RELEASE_APK_KEY_PASSWORD_FILE"
) ?: apkStorePasswordArgument

val hasBundleSigningConfig = bundleStoreFile.isFile &&
    !bundleStorePassword.isNullOrBlank() &&
    !bundleKeyAlias.isNullOrBlank() &&
    !bundleKeyPassword.isNullOrBlank()

val hasApkSigningConfig = apkStoreFile.isFile &&
    !apkStorePassword.isNullOrBlank() &&
    !apkKeyAlias.isNullOrBlank() &&
    !apkKeyPassword.isNullOrBlank() &&
    !apkStorePasswordArgument.isNullOrBlank() &&
    !apkKeyPasswordArgument.isNullOrBlank()

val hasReleaseSigningConfig = hasBundleSigningConfig && hasApkSigningConfig

fun requireReleaseSigningConfig() {
    if (!hasReleaseSigningConfig) {
        throw GradleException(
            """
            Missing Android release signing configuration.

            Create /Users/sus/scratch/android-release-papp/signing.properties, or pass Gradle
            properties/environment variables, with:
              papp.release.bundle.store.file=$bundleStoreFilePath
              papp.release.bundle.store.password.file=/path/to/publish-store-password.txt
              papp.release.bundle.key.alias=<alias in papp-publish.jks>
              papp.release.bundle.key.password.file=/path/to/publish-key-password.txt

              papp.release.apk.store.file=$apkStoreFilePath
              papp.release.apk.store.password.file=/path/to/signing-store-password.txt
              papp.release.apk.key.alias=<alias in papp-signing.jks>
              papp.release.apk.key.password.file=/path/to/signing-key-password.txt

            Key password files are optional when the key password matches the store password.
            You can also use password properties directly, but password files avoid putting
            secrets on command lines.
            """.trimIndent()
        )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "xyz.lilsus.papp.ComposeApp")
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.window)
            implementation(libs.androidx.window.core)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.materialIconsExtended)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.nwc)
            implementation(libs.multiplatform.settings)
            implementation(libs.bitcoin.kmp)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.websockets)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "xyz.lilsus.papp"
    compileSdk = 36

    defaultConfig {
        applicationId = "xyz.lilsus.papp"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "1.2"
        ndk {
            //noinspection ChromeOsAbiSupport
            // FIXME: Support 32bit once ML Kit & acinq-secp256k1 ship 16KB-aligned natives
            abiFilters += listOf("arm64-v8a")
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = bundleStoreFile
                storePassword = bundleStorePassword
                keyAlias = bundleKeyAlias
                keyPassword = bundleKeyPassword
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Install debug separately so onboarding and wallet storage stay isolated from release.
            applicationIdSuffix = ".dev"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                // Default file with automatically generated optimization rules.
                getDefaultProguardFile("proguard-android-optimize.txt"),
                file("proguard-rules.pro")
            )

            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }

            ndk {
                debugSymbolLevel = "full"
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

tasks.register("printReleaseSigningConfig") {
    group = "publishing"
    description = "Prints the Android release signing configuration status without secrets."
    notCompatibleWithConfigurationCache(
        "Prints local signing configuration from external secret files."
    )

    doLast {
        println("Release signing properties: ${releaseSigningPropertiesFile?.path ?: "not found"}")
        println(
            "Bundle keystore: ${bundleStoreFile.path} (${if (bundleStoreFile.isFile) "found" else "missing"})"
        )
        println("Bundle key alias: ${bundleKeyAlias ?: "missing"}")
        println(
            "Bundle store password: ${if (bundleStorePassword.isNullOrBlank()) "missing" else "configured"}"
        )
        println(
            "Bundle key password: ${if (bundleKeyPassword.isNullOrBlank()) "missing" else "configured"}"
        )
        println(
            "APK keystore: ${apkStoreFile.path} (${if (apkStoreFile.isFile) "found" else "missing"})"
        )
        println("APK key alias: ${apkKeyAlias ?: "missing"}")
        println(
            "APK store password: ${if (apkStorePassword.isNullOrBlank()) "missing" else "configured"}"
        )
        println(
            "APK key password: ${if (apkKeyPassword.isNullOrBlank()) "missing" else "configured"}"
        )
        println("Release signing ready: $hasReleaseSigningConfig")
    }
}

gradle.taskGraph.whenReady {
    val releaseSigningTasks = setOf(
        ":composeApp:buildSignedReleaseBundle",
        ":composeApp:buildReleaseApksForConnectedDevice",
        ":composeApp:installSignedReleaseApks",
        ":composeApp:installSignedReleaseApk"
    )

    if (allTasks.any { it.path in releaseSigningTasks }) {
        requireReleaseSigningConfig()
    }
}

tasks.register("buildSignedReleaseBundle") {
    group = "publishing"
    description = "Builds the signed release Android App Bundle."
    notCompatibleWithConfigurationCache(
        "Uses local signing configuration from external secret files."
    )
    dependsOn("bundleRelease")

    doLast {
        println(
            "Signed release bundle: ${layout.buildDirectory.file(
                "outputs/bundle/release/composeApp-release.aab"
            ).get().asFile}"
        )
    }
}

tasks.register<Exec>("buildReleaseApksForConnectedDevice") {
    group = "publishing"
    description =
        "Builds a signed APK set from the release bundle for the connected Android device."
    notCompatibleWithConfigurationCache(
        "Uses local signing configuration from external secret files."
    )
    dependsOn("buildSignedReleaseBundle")

    val bundleFile = layout.buildDirectory.file("outputs/bundle/release/composeApp-release.aab")
    val apksFile = layout.buildDirectory.file("outputs/apks/release/composeApp-release.apks")

    doFirst {
        requireReleaseSigningConfig()
        apksFile.get().asFile.parentFile.mkdirs()
    }

    executable = "bundletool"
    args(
        "build-apks",
        "--bundle=${bundleFile.get().asFile}",
        "--output=${apksFile.get().asFile}",
        "--overwrite",
        "--connected-device",
        "--ks=$apkStoreFilePath",
        "--ks-key-alias=$apkKeyAlias",
        "--ks-pass=$apkStorePasswordArgument",
        "--key-pass=$apkKeyPasswordArgument"
    )
}

tasks.register<Exec>("installSignedReleaseApks") {
    group = "publishing"
    description = "Installs the signed release APK set on the connected Android device."
    notCompatibleWithConfigurationCache("Uses a local bundletool APK set.")
    dependsOn("buildReleaseApksForConnectedDevice")

    val apksFile = layout.buildDirectory.file("outputs/apks/release/composeApp-release.apks")

    doFirst {
        requireReleaseSigningConfig()
    }

    executable = "bundletool"
    args(
        "install-apks",
        "--apks=${apksFile.get().asFile}"
    )
}

tasks.register("installSignedReleaseApk") {
    group = "publishing"
    description = "Alias for installSignedReleaseApks."
    notCompatibleWithConfigurationCache("Alias for local release installation helper.")
    dependsOn("installSignedReleaseApks")
}
