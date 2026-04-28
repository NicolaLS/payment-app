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
    // Hook source is at git root level: payment-app/scripts/hooks/
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

val iosE2eDerivedDataPath = layout.buildDirectory.dir("ios-e2e-derived")
val iosE2eAppPath = iosE2eDerivedDataPath.map {
    it.file("Build/Products/Debug-iphonesimulator/papp.app").asFile
}

tasks.register<Exec>("buildE2eIos") {
    group = "e2e"
    description = "Builds the iOS simulator app with the e2e bundle id and e2e hooks enabled."
    notCompatibleWithConfigurationCache("Wraps local xcodebuild with dynamic derived-data paths.")

    doFirst {
        iosE2eDerivedDataPath.get().asFile.mkdirs()
    }

    executable = "xcodebuild"
    args(
        "-project",
        "iosApp/iosApp.xcodeproj",
        "-scheme",
        "iosApp",
        "-configuration",
        "Debug",
        "-sdk",
        "iphonesimulator",
        "-destination",
        "generic/platform=iOS Simulator",
        "-derivedDataPath",
        iosE2eDerivedDataPath.get().asFile.path,
        "PRODUCT_BUNDLE_IDENTIFIER=xyz.lilsus.papp.e2e",
        "INFOPLIST_KEY_CFBundleDisplayName=Lasr E2E",
        "build"
    )
}

tasks.register<Exec>("installE2eIos") {
    group = "e2e"
    description = "Builds and installs the iOS e2e app on the booted simulator."
    notCompatibleWithConfigurationCache("Wraps local simctl installation.")
    dependsOn("buildE2eIos")

    doFirst {
        val app = iosE2eAppPath.get()
        require(app.exists()) {
            "Expected iOS e2e app at ${app.path}; run buildE2eIos first."
        }
    }

    executable = "xcrun"
    args(
        "simctl",
        "install",
        providers.gradleProperty("papp.ios.simulator").orElse("booted").get(),
        iosE2eAppPath.get().path
    )
}
