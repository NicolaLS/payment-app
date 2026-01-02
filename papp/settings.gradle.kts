rootProject.name = "papp"
// Disabled due to naming conflict with nwc-kmp composite build
// enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenLocal()
        // maven("https://central.sonatype.com/repository/maven-snapshots/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
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

include(":composeApp")

// Include nwc-kmp for local development
includeBuild("../../../../Nostr/nwc-kmp") {
    dependencySubstitution {
        substitute(module("io.github.nicolals:nwc-kmp")).using(project(":nwc-kmp"))
    }
}
