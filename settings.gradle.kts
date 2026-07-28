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

includeApp("blip")
includeApp("lasr")

include(":blip:feature:onboarding")
project(":blip:feature:onboarding").projectDir = file("apps/blip/feature/onboarding")
include(":blip:feature:payment")
project(":blip:feature:payment").projectDir = file("apps/blip/feature/payment")
include(":blip:feature:blink-contacts")
project(":blip:feature:blink-contacts").projectDir = file("apps/blip/feature/blink-contacts")
include(":blip:feature:wallet-connection")
project(":blip:feature:wallet-connection").projectDir = file("apps/blip/feature/wallet-connection")
include(":blip:feature:wallet-details")
project(":blip:feature:wallet-details").projectDir = file("apps/blip/feature/wallet-details")
include(":blip:integration:blink")
project(":blip:integration:blink").projectDir = file("apps/blip/integration/blink")
include(":blip:ui")
project(":blip:ui").projectDir = file("apps/blip/ui")
include(":lasr:feature:onboarding")
project(":lasr:feature:onboarding").projectDir = file("apps/lasr/feature/onboarding")
include(":lasr:feature:payment")
project(":lasr:feature:payment").projectDir = file("apps/lasr/feature/payment")
include(":lasr:feature:wallet-connection")
project(":lasr:feature:wallet-connection").projectDir =
    file("apps/lasr/feature/wallet-connection")
include(":lasr:feature:wallet-details")
project(":lasr:feature:wallet-details").projectDir =
    file("apps/lasr/feature/wallet-details")
include(":lasr:integration:nwc")
project(":lasr:integration:nwc").projectDir = file("apps/lasr/integration/nwc")

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
include(":feature:payment-intent")
include(":feature:settings")
include(":feature:payment-settings")
include(":feature:payment-shortcuts")
include(":feature:contacts")
include(":feature:wallet-management")
include(":integration:exchange-rate")
include(":integration:lnurl")
