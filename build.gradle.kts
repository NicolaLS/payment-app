import org.gradle.api.artifacts.ProjectDependency
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.apollo) apply false
    alias(libs.plugins.mokkery) apply false
    alias(libs.plugins.breezSpark) apply false
    alias(libs.plugins.sqldelight) apply false
    id("xyz.lilsus.raylsuite.kover")
}

val ktlintCliVersion = libs.versions.ktlintCli
val appProjectNames = setOf("blip", "flint", "lasr")

fun Project.configureKtlint() {
    extensions.configure<KtlintExtension> {
        version.set(ktlintCliVersion)
        android.set(true)
        outputToConsole.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)
        filter {
            exclude { it.file.path.contains("/build/") }
        }
    }
}

allprojects {
    pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
        configureKtlint()
    }
}

fun String.appOwner(): String? = removePrefix(":")
    .substringBefore(":")
    .takeIf(appProjectNames::contains)

val verifyModuleDependencies = tasks.register("verifyModuleDependencies") {
    group = "verification"
    description = "Rejects root-to-app and cross-app project dependencies."

    doLast {
        val violations =
            subprojects.flatMap { source ->
                val sourceOwner = source.path.appOwner()
                source.configurations.flatMap { configuration ->
                    configuration.dependencies
                        .withType(ProjectDependency::class.java)
                        .mapNotNull { dependency ->
                            val targetPath = dependency.path
                            val targetOwner = targetPath.appOwner()
                            when {
                                sourceOwner == null && targetOwner != null ->
                                    "${source.path} -> $targetPath (root module depends on an app)"

                                sourceOwner != null &&
                                    targetOwner != null &&
                                    sourceOwner != targetOwner ->
                                    "${source.path} -> $targetPath (cross-app dependency)"

                                else -> null
                            }
                        }
                }
            }.distinct()
                .sorted()

        check(violations.isEmpty()) {
            buildString {
                appendLine("Invalid project dependencies:")
                violations.forEach { appendLine("  - $it") }
            }
        }
    }
}

tasks.register("check") {
    group = "verification"
    description = "Runs the root architecture checks."
    dependsOn(verifyModuleDependencies)
}
