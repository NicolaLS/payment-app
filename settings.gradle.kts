rootProject.name = "rayl-suite"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        maven("https://mvn.breez.technology/releases")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        maven("https://mvn.breez.technology/releases")
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

fun includeApp(name: String, modules: List<String>) {
    val appPath = ":$name"
    val allModules = listOf("shared", "androidApp") + modules

    include(allModules.map { module -> "$appPath:$module" })

    project(appPath).projectDir = file("apps/$name")

    allModules
        .flatMap { module ->
            val segments = module.split(":")
            segments.indices.map { index ->
                segments.take(index + 1).joinToString(":")
            }
        }.distinct()
        .forEach { module ->
            project("$appPath:$module").projectDir =
                file("apps/$name/${module.replace(':', '/')}")
        }
}

includeApp(
    name = "blip",
    modules =
        listOf(
            "feature:onboarding",
            "feature:payment",
            "feature:blink-contacts",
            "feature:wallet-connection",
            "feature:wallet-details",
            "integration:blink",
            "ui"
        )
)
includeApp(
    name = "flint",
    modules =
        listOf(
            "application",
            "feature:onboarding",
            "feature:payment",
            "feature:wallet-connection",
            "integration:wallet"
        )
)
includeApp(
    name = "lasr",
    modules =
        listOf(
            "feature:onboarding",
            "feature:payment",
            "feature:wallet-connection",
            "feature:wallet-details",
            "integration:nwc"
        )
)

include(":core:model")
include(":core:camera")
include(":core:network")
include(":core:payment")
include(":core:settings")
include(":core:ui")
include(":feature:theme-settings")
include(":feature:currency-settings")
include(":feature:language-settings")
include(":feature:onboarding")
include(":feature:payment-ui")
include(":feature:settings")
include(":feature:payment-settings")
include(":feature:payment-shortcuts")
include(":feature:contacts")
include(":feature:wallet-management")
include(":integration:exchange-rate")
include(":integration:lnurl")
