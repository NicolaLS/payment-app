plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.apollo) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        android.set(true)
        outputToConsole.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)
        filter {
            exclude { it.file.path.contains("/build/") }
        }
    }
}

tasks.register<Copy>("installGitHooks") {
    description = "Install git hooks for code quality checks"
    group = "git hooks"
    // Hook source is at git root level: lasr/scripts/hooks/
    from(file("../scripts/hooks/pre-commit"))
    into(file("../.git/hooks"))
    filePermissions {
        user {
            read = true
            write = true
            execute = true
        }
        group {
            read = true
            execute = true
        }
        other {
            read = true
            execute = true
        }
    }
}

tasks.named("prepareKotlinBuildScriptModel") {
    dependsOn("installGitHooks")
}

val iosE2eDerivedDataPath = "ios-e2e-derived"
val iosE2eAppPath = "$iosE2eDerivedDataPath/Build/Products/Debug-iphonesimulator/Lasr E2E.app"

tasks.register<Exec>("buildE2eIos") {
    group = "e2e"
    description = "Builds the dedicated iOS simulator e2e app."
    notCompatibleWithConfigurationCache("Wraps local xcodebuild with dynamic derived-data paths.")

    val derivedDataPath = layout.buildDirectory.dir(iosE2eDerivedDataPath).get().asFile

    executable = "xcodebuild"
    args(
        "-project",
        "iosApp/iosApp.xcodeproj",
        "-scheme",
        "iosAppE2E",
        "-configuration",
        "Debug",
        "-sdk",
        "iphonesimulator",
        "-destination",
        "generic/platform=iOS Simulator",
        "-derivedDataPath",
        derivedDataPath.path,
        "build"
    )
}

tasks.register<Exec>("installE2eIos") {
    group = "e2e"
    description = "Builds and installs the iOS e2e app on the booted simulator."
    notCompatibleWithConfigurationCache("Wraps local simctl installation.")
    dependsOn("buildE2eIos")

    val appPath = layout.buildDirectory.file(iosE2eAppPath).get().asFile

    doFirst {
        require(appPath.exists()) {
            "Expected iOS e2e app at ${appPath.path}; run buildE2eIos first."
        }
    }

    executable = "xcrun"
    args(
        "simctl",
        "install",
        providers.gradleProperty("lasr.ios.simulator").orElse("booted").get(),
        appPath.path
    )
}
