plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktlint)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("xyz.lilsus.raylsuite.backend.hub.HubServerKt")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
}

dependencies {
    implementation(project(":core:hub-api"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.lightning.kmp.core)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.ktor.server.testHost)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
