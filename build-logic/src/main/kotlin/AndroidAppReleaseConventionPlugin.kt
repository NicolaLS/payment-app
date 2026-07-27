import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import java.io.File
import java.util.Properties

/**
 * Local release signing for the Blip and Lasr Android applications.
 *
 * Two identities are involved, and they are deliberately different:
 *  - `RAYL_UPLOAD_*` signs the App Bundle that is uploaded to Play.
 *  - `RAYL_APP_SIGNING_*` signs the APKs distributed outside Play, so that a sideloaded
 *    install carries the same signature as a Play install and stays update compatible.
 */
class AndroidAppReleaseConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.withPlugin("com.android.application") {
            target.configureReleaseSigning()
        }
    }
}

private const val PUBLISHING_GROUP = "publishing"
private const val UPLOAD_PREFIX = "RAYL_UPLOAD"
private const val APP_SIGNING_PREFIX = "RAYL_APP_SIGNING"

private const val VALIDATE = "validateReleaseSigning"
private const val PRINT_CONFIG = "printReleaseSigningConfig"
private const val BUILD_BUNDLE = "buildSignedReleaseBundle"
private const val BUILD_UNIVERSAL_APKS = "buildUniversalReleaseApks"
private const val BUILD_UNIVERSAL_APK = "buildUniversalReleaseApk"
private const val BUILD_DEVICE_APKS = "buildReleaseApksForConnectedDevice"
private const val INSTALL_SIGNED_RELEASE = "installSignedRelease"
private const val BUILD_GITHUB_ARTIFACTS = "buildGithubReleaseArtifacts"

private const val NO_CONFIG_CACHE =
    "Reads local signing secrets at execution time; they must not be cached."

private fun Project.configureReleaseSigning() {
    val upload = ReleaseSigningIdentity(providers, UPLOAD_PREFIX)
    val appSigning = ReleaseSigningIdentity(providers, APP_SIGNING_PREFIX)
    val appName = parent?.name ?: name

    applyUploadSigningConfig(upload)

    val bundleFile = objects.fileProperty()
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants(selector().withBuildType("release")) { variant ->
            bundleFile.set(variant.artifacts.get(SingleArtifact.BUNDLE))
        }
    }

    val universalApksFile = layout.buildDirectory
        .file("outputs/apks/universal/release/$appName-release-universal.apks")
    val universalApkDir = layout.buildDirectory.dir("outputs/apk/universal/release")
    val universalApkFile = universalApkDir.map { it.file("$appName-release-universal.apk") }
    val deviceApksFile = layout.buildDirectory
        .file("outputs/apks/release/$appName-release.apks")
    val passwordScratch = layout.buildDirectory.dir("tmp/rayl-signing")
    val adb = resolveAdbExecutable()

    tasks.register<PrintReleaseSigningConfigTask>(PRINT_CONFIG) {
        group = PUBLISHING_GROUP
        description = "Prints Android release signing status without revealing secrets."
        notCompatibleWithConfigurationCache(NO_CONFIG_CACHE)
        statusLines.set(
            provider {
                upload.statusLines("Upload") +
                    appSigning.statusLines("App signing") +
                    "Release signing ready: ${upload.isConfigured && appSigning.isConfigured}"
            },
        )
    }

    val validate = tasks.register(VALIDATE) {
        description = "Fails fast when local release signing is not fully configured."
        notCompatibleWithConfigurationCache(NO_CONFIG_CACHE)
        doLast {
            requireSigningIdentities(upload, appSigning)
            requireBundletool()
        }
    }

    // Fail before spending a full R8 release build on a misconfigured environment.
    // AGP registers bundleRelease while creating variants, so match it lazily.
    tasks.matching { it.name == "bundleRelease" }.configureEach { mustRunAfter(validate) }

    val buildBundle = tasks.register(BUILD_BUNDLE) {
        group = PUBLISHING_GROUP
        description = "Builds the upload-key signed release Android App Bundle for Play."
        notCompatibleWithConfigurationCache(NO_CONFIG_CACHE)
        dependsOn(validate, "bundleRelease")
        doLast { logger.lifecycle("Signed release bundle: ${bundleFile.get().asFile}") }
    }

    val universalApks = tasks.register<BundletoolBuildApksTask>(BUILD_UNIVERSAL_APKS) {
        group = PUBLISHING_GROUP
        description = "Builds the app-signing-key signed universal release APK set."
        notCompatibleWithConfigurationCache(NO_CONFIG_CACHE)
        dependsOn(buildBundle)
        this.bundleFile.set(bundleFile)
        apksFile.set(universalApksFile)
        universal.set(true)
        applySigningIdentity(appSigning)
        passwordScratchDir.set(passwordScratch)
    }

    val universalApk = tasks.register<Copy>(BUILD_UNIVERSAL_APK) {
        group = PUBLISHING_GROUP
        description = "Extracts the signed universal release APK for direct distribution."
        notCompatibleWithConfigurationCache(NO_CONFIG_CACHE)
        dependsOn(universalApks)
        from(provider { zipTree(universalApksFile.get().asFile) })
        include("universal.apk")
        into(universalApkDir)
        rename("universal.apk", "$appName-release-universal.apk")
        doLast { logger.lifecycle("Signed universal APK: ${universalApkFile.get().asFile}") }
    }

    val deviceApks = tasks.register<BundletoolBuildApksTask>(BUILD_DEVICE_APKS) {
        group = PUBLISHING_GROUP
        description = "Builds a signed APK set targeting the connected Android device."
        notCompatibleWithConfigurationCache(NO_CONFIG_CACHE)
        dependsOn(buildBundle)
        this.bundleFile.set(bundleFile)
        apksFile.set(deviceApksFile)
        universal.set(false)
        applySigningIdentity(appSigning)
        passwordScratchDir.set(passwordScratch)
        adbExecutable.set(adb)
    }

    tasks.register<BundletoolInstallApksTask>(INSTALL_SIGNED_RELEASE) {
        group = PUBLISHING_GROUP
        description = "Installs the app-signing-key signed release build on a connected device."
        notCompatibleWithConfigurationCache(NO_CONFIG_CACHE)
        dependsOn(deviceApks)
        apksFile.set(deviceApksFile)
        adbExecutable.set(adb)
    }

    tasks.register(BUILD_GITHUB_ARTIFACTS) {
        group = PUBLISHING_GROUP
        description = "Builds the Play bundle and the universal APK for a GitHub release."
        notCompatibleWithConfigurationCache(NO_CONFIG_CACHE)
        dependsOn(buildBundle, universalApk)
        doLast {
            logger.lifecycle("Release artifacts:")
            logger.lifecycle("  ${bundleFile.get().asFile}")
            logger.lifecycle("  ${universalApkFile.get().asFile}")
        }
    }
}

private fun Project.applyUploadSigningConfig(upload: ReleaseSigningIdentity) {
    if (!upload.isConfigured) return

    extensions.configure<ApplicationExtension> {
        val uploadConfig = signingConfigs.create("upload")
        uploadConfig.storeFile = upload.keystore
        uploadConfig.storePassword = upload.resolveStorePassword()
        uploadConfig.keyAlias = upload.keyAlias.get()
        uploadConfig.keyPassword = upload.resolveKeyPassword()

        buildTypes.getByName("release").signingConfig = uploadConfig
    }
}

private fun BundletoolBuildApksTask.applySigningIdentity(identity: ReleaseSigningIdentity) {
    keystore.set(identity.storeFile)
    keyAlias.set(identity.keyAlias)
    storePassword.set(identity.storePassword)
    storePasswordFile.set(identity.storePasswordFile)
    keyPassword.set(identity.keyPassword)
    keyPasswordFile.set(identity.keyPasswordFile)
}

private fun Task.requireSigningIdentities(
    upload: ReleaseSigningIdentity,
    appSigning: ReleaseSigningIdentity,
) {
    val missing = upload.missingVariables() + appSigning.missingVariables()
    if (missing.isEmpty()) return

    throw GradleException(
        buildString {
            appendLine("Local Android release signing is not configured.")
            appendLine()
            appendLine("Missing:")
            missing.forEach { appendLine("  - $it") }
            appendLine()
            append("Copy .envrc.example to .envrc.local and load the passwords from a ")
            append("secret manager, then re-run. See docs/release.md.")
        },
    )
}

private fun Task.requireBundletool() {
    val onPath = System.getenv("PATH")
        .orEmpty()
        .split(File.pathSeparator)
        .any { File(it, BUNDLETOOL).canExecute() }

    if (!onPath) {
        throw GradleException(
            "bundletool is required to build signed release APKs. Install it with " +
                "`brew install bundletool` or from https://github.com/google/bundletool.",
        )
    }
}

private fun Project.resolveAdbExecutable(): String? =
    listOfNotNull(
        providers.environmentVariable("ANDROID_HOME").orNull,
        providers.environmentVariable("ANDROID_SDK_ROOT").orNull,
        localPropertiesSdkDir(),
    ).map { File(it, "platform-tools/adb") }
        .firstOrNull { it.canExecute() }
        ?.absolutePath

private fun Project.localPropertiesSdkDir(): String? {
    val localProperties = rootProject.file("local.properties")
    if (!localProperties.isFile) return null

    val properties = Properties()
    localProperties.inputStream().use(properties::load)
    return properties.getProperty("sdk.dir")
}
