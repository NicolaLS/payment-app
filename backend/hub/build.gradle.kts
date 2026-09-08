plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.ktlint)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("xyz.lilsus.raylsuite.backend.hub.HubServerKt")
}

dependencies {
    implementation(project(":core:hub-api"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.ktor.server.testHost)
}
