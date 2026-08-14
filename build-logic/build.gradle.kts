plugins {
    `kotlin-dsl`
}

group = "xyz.lilsus.raylsuite.buildlogic"

dependencies {
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    implementation(
        "org.jetbrains.compose:compose-gradle-plugin:${libs.versions.composeMultiplatform.get()}",
    )
    implementation(
        "org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}",
    )
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:${libs.versions.ktlint.get()}")
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "xyz.lilsus.raylsuite.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpComposeLibrary") {
            id = "xyz.lilsus.raylsuite.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("appShared") {
            id = "xyz.lilsus.raylsuite.app.shared"
            implementationClass = "AppSharedConventionPlugin"
        }
        register("androidApplication") {
            id = "xyz.lilsus.raylsuite.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidAppRelease") {
            id = "xyz.lilsus.raylsuite.android.release"
            implementationClass = "AndroidAppReleaseConventionPlugin"
        }
    }
}
