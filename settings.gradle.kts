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

include(":core:model")
include(":core:settings")
include(":core:ui")
include(":feature:theme-settings")
