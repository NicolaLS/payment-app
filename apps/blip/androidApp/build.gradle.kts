import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    id("org.jetbrains.kotlinx.kover")
}

abstract class PrintReleaseSigningConfigTask : DefaultTask() {
    @get:Input
    abstract val statusLines: ListProperty<String>

    @TaskAction
    fun printStatus() {
        statusLines.get().forEach(logger::lifecycle)
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val releaseSigningPropertiesFile = providers.gradleProperty("blip.release.signing.properties")
    .orElse(providers.environmentVariable("BLIP_ANDROID_SIGNING_PROPERTIES"))
    .map { file(it) }
    .orNull
    ?: file("/Users/sus/scratch/android-release-blip/signing.properties").takeIf { it.isFile }

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
    propertyName = "blip.release.bundle.store.file",
    environmentName = "BLIP_RELEASE_BUNDLE_STORE_FILE"
) ?: releaseSigningValue(
    propertyName = "blip.release.store.file",
    environmentName = "BLIP_RELEASE_STORE_FILE"
) ?: "/Users/sus/scratch/android-release-blip/publish-key/blip-publish.jks"
val bundleStoreFile = file(bundleStoreFilePath)
val bundleStorePassword = releaseSigningPassword(
    propertyName = "blip.release.bundle.store.password",
    environmentName = "BLIP_RELEASE_BUNDLE_STORE_PASSWORD",
    filePropertyName = "blip.release.bundle.store.password.file",
    fileEnvironmentName = "BLIP_RELEASE_BUNDLE_STORE_PASSWORD_FILE"
) ?: releaseSigningPassword(
    propertyName = "blip.release.store.password",
    environmentName = "BLIP_RELEASE_STORE_PASSWORD",
    filePropertyName = "blip.release.store.password.file",
    fileEnvironmentName = "BLIP_RELEASE_STORE_PASSWORD_FILE"
)
val bundleKeyAlias = releaseSigningValue(
    propertyName = "blip.release.bundle.key.alias",
    environmentName = "BLIP_RELEASE_BUNDLE_KEY_ALIAS"
) ?: releaseSigningValue(
    propertyName = "blip.release.key.alias",
    environmentName = "BLIP_RELEASE_KEY_ALIAS"
)
val bundleKeyPassword = releaseSigningPassword(
    propertyName = "blip.release.bundle.key.password",
    environmentName = "BLIP_RELEASE_BUNDLE_KEY_PASSWORD",
    filePropertyName = "blip.release.bundle.key.password.file",
    fileEnvironmentName = "BLIP_RELEASE_BUNDLE_KEY_PASSWORD_FILE"
) ?: releaseSigningPassword(
    propertyName = "blip.release.key.password",
    environmentName = "BLIP_RELEASE_KEY_PASSWORD",
    filePropertyName = "blip.release.key.password.file",
    fileEnvironmentName = "BLIP_RELEASE_KEY_PASSWORD_FILE"
) ?: bundleStorePassword

val apkStoreFilePath = releaseSigningValue(
    propertyName = "blip.release.apk.store.file",
    environmentName = "BLIP_RELEASE_APK_STORE_FILE"
) ?: "/Users/sus/scratch/android-release-blip/release-key/blip-signing.jks"
val apkStoreFile = file(apkStoreFilePath)
val apkStorePassword = releaseSigningPassword(
    propertyName = "blip.release.apk.store.password",
    environmentName = "BLIP_RELEASE_APK_STORE_PASSWORD",
    filePropertyName = "blip.release.apk.store.password.file",
    fileEnvironmentName = "BLIP_RELEASE_APK_STORE_PASSWORD_FILE"
) ?: bundleStorePassword
val apkKeyAlias = releaseSigningValue(
    propertyName = "blip.release.apk.key.alias",
    environmentName = "BLIP_RELEASE_APK_KEY_ALIAS"
) ?: bundleKeyAlias
val apkKeyPassword = releaseSigningPassword(
    propertyName = "blip.release.apk.key.password",
    environmentName = "BLIP_RELEASE_APK_KEY_PASSWORD",
    filePropertyName = "blip.release.apk.key.password.file",
    fileEnvironmentName = "BLIP_RELEASE_APK_KEY_PASSWORD_FILE"
) ?: apkStorePassword

val apkStorePasswordArgument = releaseSigningPasswordArgument(
    password = apkStorePassword,
    filePropertyName = "blip.release.apk.store.password.file",
    fileEnvironmentName = "BLIP_RELEASE_APK_STORE_PASSWORD_FILE"
)
val apkKeyPasswordArgument = releaseSigningPasswordArgument(
    password = apkKeyPassword,
    filePropertyName = "blip.release.apk.key.password.file",
    fileEnvironmentName = "BLIP_RELEASE_APK_KEY_PASSWORD_FILE"
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

            Create /Users/sus/scratch/android-release-blip/signing.properties, or pass Gradle
            properties/environment variables, with:
              blip.release.bundle.store.file=$bundleStoreFilePath
              blip.release.bundle.store.password.file=/path/to/publish-store-password.txt
              blip.release.bundle.key.alias=<alias in blip-publish.jks>
              blip.release.bundle.key.password.file=/path/to/publish-key-password.txt

              blip.release.apk.store.file=$apkStoreFilePath
              blip.release.apk.store.password.file=/path/to/signing-store-password.txt
              blip.release.apk.key.alias=<alias in blip-signing.jks>
              blip.release.apk.key.password.file=/path/to/signing-key-password.txt

            Key password files are optional when the key password matches the store password.
            You can also use password properties directly, but password files avoid putting
            secrets on command lines.
            """.trimIndent()
        )
    }
}

android {
    namespace = "xyz.lilsus.blip"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.lilsus.blip"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "2"
        manifestPlaceholders["mainActivityName"] = "xyz.lilsus.blip.MainActivity"
        manifestPlaceholders["usesCleartextTraffic"] = "false"
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
            manifestPlaceholders["appLabel"] = "Blip Dev"
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appLabel"] = "Blip"

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
        create("e2e") {
            initWith(getByName("release"))
            applicationIdSuffix = ".e2e"
            matchingFallbacks += listOf("release")
            manifestPlaceholders["appLabel"] = "Blip E2E"
            manifestPlaceholders["mainActivityName"] = "xyz.lilsus.blip.E2eMainActivity"
            manifestPlaceholders["usesCleartextTraffic"] = "true"
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
}

dependencies {
    implementation(project(":blip:shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.window)
    implementation(libs.androidx.window.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.nwc)
    debugImplementation(libs.compose.ui.tooling)
}

kover {
    currentProject {
        sources {
            excludedSourceSets.add("e2e")
        }
    }

    reports {
        total {
            filters {
                excludes {
                    packages("xyz.lilsus.blip.e2e")
                    classes("xyz.lilsus.blip.E2eMainActivity*")
                }
            }
        }
    }
}

tasks.register<PrintReleaseSigningConfigTask>("printReleaseSigningConfig") {
    group = "publishing"
    description = "Prints the Android release signing configuration status without secrets."
    statusLines.set(
        listOf(
            "Release signing properties: ${releaseSigningPropertiesFile?.path ?: "not found"}",
            "Bundle keystore: ${bundleStoreFile.path} (${if (bundleStoreFile.isFile) "found" else "missing"})",
            "Bundle key alias: ${bundleKeyAlias ?: "missing"}",
            "Bundle store password: ${if (bundleStorePassword.isNullOrBlank()) "missing" else "configured"}",
            "Bundle key password: ${if (bundleKeyPassword.isNullOrBlank()) "missing" else "configured"}",
            "APK keystore: ${apkStoreFile.path} (${if (apkStoreFile.isFile) "found" else "missing"})",
            "APK key alias: ${apkKeyAlias ?: "missing"}",
            "APK store password: ${if (apkStorePassword.isNullOrBlank()) "missing" else "configured"}",
            "APK key password: ${if (apkKeyPassword.isNullOrBlank()) "missing" else "configured"}",
            "Release signing ready: $hasReleaseSigningConfig"
        )
    )
}

gradle.taskGraph.whenReady {
    val releaseSigningTasks = setOf(
        ":androidApp:buildSignedReleaseBundle",
        ":androidApp:buildUniversalReleaseApks",
        ":androidApp:buildUniversalReleaseApk",
        ":androidApp:buildGithubReleaseArtifacts",
        ":androidApp:buildReleaseApksForConnectedDevice",
        ":androidApp:installSignedReleaseApks",
        ":androidApp:installSignedReleaseApk"
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
            "Signed release bundle: ${
                layout.buildDirectory.file(
                    "outputs/bundle/release/androidApp-release.aab"
                ).get().asFile
            }"
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

    val bundleFile = layout.buildDirectory.file("outputs/bundle/release/androidApp-release.aab")
    val apksFile = layout.buildDirectory.file("outputs/apks/release/androidApp-release.apks")

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

tasks.register<Exec>("buildUniversalReleaseApks") {
    group = "publishing"
    description = "Builds a signed universal release APK set from the release bundle."
    notCompatibleWithConfigurationCache(
        "Uses local signing configuration from external secret files."
    )
    dependsOn("buildSignedReleaseBundle")

    val bundleFile = layout.buildDirectory.file("outputs/bundle/release/androidApp-release.aab")
    val apksFile = layout.buildDirectory.file(
        "outputs/apks/universal/release/androidApp-release-universal.apks"
    )

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
        "--mode=universal",
        "--ks=$apkStoreFilePath",
        "--ks-key-alias=$apkKeyAlias",
        "--ks-pass=$apkStorePasswordArgument",
        "--key-pass=$apkKeyPasswordArgument"
    )
}

tasks.register<Copy>("buildUniversalReleaseApk") {
    group = "publishing"
    description = "Extracts the signed universal release APK for GitHub releases."
    notCompatibleWithConfigurationCache("Uses a local bundletool APK set.")
    dependsOn("buildUniversalReleaseApks")

    val apksFile = layout.buildDirectory.file(
        "outputs/apks/universal/release/androidApp-release-universal.apks"
    )
    val apkOutputDir = layout.buildDirectory.dir("outputs/apk/universal/release")

    doFirst {
        requireReleaseSigningConfig()
    }

    from(provider { zipTree(apksFile.get().asFile) })
    include("universal.apk")
    into(apkOutputDir)
    rename("universal.apk", "androidApp-release-universal.apk")

    doLast {
        println(
            "Signed universal release APK: ${
                apkOutputDir.get().file("androidApp-release-universal.apk").asFile
            }"
        )
    }
}

tasks.register("buildGithubReleaseArtifacts") {
    group = "publishing"
    description = "Builds the signed Play bundle and universal APK for a GitHub release."
    notCompatibleWithConfigurationCache("Uses local release signing helpers.")
    dependsOn("buildSignedReleaseBundle", "buildUniversalReleaseApk")

    val bundleOutput = layout.buildDirectory.file(
        "outputs/bundle/release/androidApp-release.aab"
    )
    val apkOutput = layout.buildDirectory.file(
        "outputs/apk/universal/release/androidApp-release-universal.apk"
    )

    doLast {
        println(
            "GitHub release artifacts:\n" +
                "  ${bundleOutput.get().asFile}\n" +
                "  ${apkOutput.get().asFile}"
        )
    }
}

tasks.register<Exec>("installSignedReleaseApks") {
    group = "publishing"
    description = "Installs the signed release APK set on the connected Android device."
    notCompatibleWithConfigurationCache("Uses a local bundletool APK set.")
    dependsOn("buildReleaseApksForConnectedDevice")

    val apksFile = layout.buildDirectory.file("outputs/apks/release/androidApp-release.apks")

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
