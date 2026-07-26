plugins {
    `kotlin-dsl`
}

group = "xyz.lilsus.raylsuite.buildlogic"

dependencies {
    implementation("com.android.tools.build:gradle:9.3.0")
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.11.1")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.10")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
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
    }
}
