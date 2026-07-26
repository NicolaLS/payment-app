rootProject.name = "rayl-suite"

pluginManagement {
    includeBuild("build-logic")

    repositories {
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

fun includeApp(name: String) {
    val appPath = ":$name"
    val modules = listOf("shared", "androidApp")

    include(modules.map { module -> "$appPath:$module" })

    project(appPath).projectDir = file("apps/$name")

    modules.forEach { module ->
        project("$appPath:$module").projectDir =
        file("apps/$name/$module")
    }
}

includeApp("legacy")
includeApp("blip")
includeApp("lasr")

include(":blip:feature:onboarding")
project(":blip:feature:onboarding").projectDir = file("apps/blip/feature/onboarding")
include(":lasr:feature:onboarding")
project(":lasr:feature:onboarding").projectDir = file("apps/lasr/feature/onboarding")

include(":core:model")
include(":core:network")
include(":core:payment")
include(":core:settings")
include(":core:ui")
include(":feature:theme-settings")
include(":feature:currency-settings")
include(":feature:language-settings")
include(":feature:onboarding")
include(":feature:settings")
include(":feature:payment-settings")
include(":feature:payment-shortcuts")
include(":feature:contacts")
include(":integration:exchange-rate")
