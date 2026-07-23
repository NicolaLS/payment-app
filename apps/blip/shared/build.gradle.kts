import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.apollo)
    alias(libs.plugins.sqldelight)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvmToolchain(21)

    android {
        namespace = "xyz.lilsus.rayl.blip.shared"
        compileSdk = 37
        minSdk = 29

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }

        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "BlipShared"
            isStatic = true
            binaryOption("bundleId", "xyz.lilsus.blip.shared")
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val androidMain by getting {
            dependsOn(commonMain)
        }
        val androidHostTest by getting {
            dependsOn(commonTest)
        }
        val iosMain by creating {
            dependsOn(commonMain)
        }
        val iosTest by creating {
            dependsOn(commonTest)
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosArm64Test by getting {
            dependsOn(iosTest)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest)
        }

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.bitcoin.kmp)
            implementation(libs.lightning.kmp.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.uri.kmp)
            implementation(libs.apollo.runtime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.secp256k1.kmp.jni.android)
            implementation(libs.sqldelight.android.driver)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    packageOfResClass = "xyz.lilsus.rayl.blip.generated.resources"
}

apollo {
    service("blink") {
        val schema = file(
            "src/commonMain/graphql/xyz/lilsus/rayl/blip/data/blink/graphql/schema.graphqls"
        )

        packageName.set("xyz.lilsus.rayl.blip.data.blink.graphql")
        schemaFile.set(schema)
        generateAsInternal.set(true)
        mapScalar("LnPaymentRequest", "kotlin.String", "com.apollographql.apollo.api.StringAdapter")
        mapScalar("WalletId", "kotlin.String", "com.apollographql.apollo.api.StringAdapter")
        mapScalar("PaymentHash", "kotlin.String", "com.apollographql.apollo.api.StringAdapter")
        mapScalar(
            "LnPaymentPreImage",
            "kotlin.String",
            "com.apollographql.apollo.api.StringAdapter"
        )
        mapScalar("Memo", "kotlin.String", "com.apollographql.apollo.api.StringAdapter")
        mapScalar("ContactAlias", "kotlin.String", "com.apollographql.apollo.api.StringAdapter")
        mapScalar("ContactHandle", "kotlin.String", "com.apollographql.apollo.api.StringAdapter")
        mapScalar("SatAmount", "kotlin.Long", "com.apollographql.apollo.api.LongAdapter")
        mapScalar("SignedAmount", "kotlin.Long", "com.apollographql.apollo.api.LongAdapter")
    }
}

sqldelight {
    databases {
        create("BlipDatabase") {
            packageName.set("xyz.lilsus.rayl.blip.data.db")
        }
    }
}

tasks.register("verifyBlipArchitecture") {
    group = "verification"
    description = "Checks Blip layer, provider, and intentionally-small test boundaries."

    doLast {
        val sourceRoot = projectDir.resolve("src")
        val domainFiles = sourceRoot.resolve("commonMain/kotlin/xyz/lilsus/rayl/blip/domain")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
        val forbiddenDomainImports = listOf(
            "androidx.compose",
            "com.apollographql",
            "com.russhwolf.settings",
            "io.ktor",
            "org.koin",
            "xyz.lilsus.rayl.blip.data",
            "xyz.lilsus.rayl.blip.platform",
            "xyz.lilsus.rayl.blip.presentation"
        )
        val violations = domainFiles.flatMap { file ->
            file.readLines()
                .filter { line ->
                    line.startsWith("import ") &&
                        forbiddenDomainImports.any(line::contains)
                }
                .map { line -> "${file.relativeTo(projectDir)}: $line" }
        }.toList()
        check(violations.isEmpty()) {
            "Blip domain layer has forbidden imports:\n${violations.joinToString("\n")}"
        }

        val dependencyText = buildFile.readText()
        val forbiddenCatalogAlias = "libs." + "nwc"
        val forbiddenArtifact = "nwc" + "-kmp"
        check(
            forbiddenCatalogAlias !in dependencyText &&
                forbiddenArtifact !in dependencyText
        ) {
            "Blip must not depend on an NWC artifact."
        }
        val kotlinImports = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter(String::startsWithImport)
                    .map { file to it }
            }
            .filter { (_, line) ->
                line.contains(".nwc.", ignoreCase = true) ||
                    line.contains("nwc_kmp", ignoreCase = true)
            }
            .toList()
        check(kotlinImports.isEmpty()) {
            "Blip source imports an NWC implementation: $kotlinImports"
        }

        val prohibitedTests = listOf(
            sourceRoot.resolve("commonTest"),
            sourceRoot.resolve("androidHostTest"),
            sourceRoot.resolve("androidInstrumentedTest"),
            projectDir.parentFile.resolve("flows"),
            projectDir.parentFile.resolve("e2e")
        ).flatMap { root ->
            if (root.exists()) {
                root.walkTopDown().filter(File::isFile).toList()
            } else {
                emptyList()
            }
        }
        check(prohibitedTests.isEmpty()) {
            "Blip extraction must not migrate integration/E2E tests: $prohibitedTests"
        }
    }
}

private fun String.startsWithImport(): Boolean = trimStart().startsWith("import ")
