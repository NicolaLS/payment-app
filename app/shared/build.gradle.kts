import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.apollo)
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvmToolchain(21)

    android {
        namespace = "xyz.lilsus.papp.shared"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources {
            enable = true
        }

        withHostTest {}
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "xyz.lilsus.papp.shared")
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
        val iosX64Main by getting {
            dependsOn(iosMain)
        }
        val iosX64Test by getting {
            dependsOn(iosTest)
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
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.nwc)
            implementation(libs.multiplatform.settings)
            implementation(libs.bitcoin.kmp)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.websockets)
            implementation(libs.apollo.runtime)
            implementation(libs.qrose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

apollo {
    service("blink") {
        val blinkSchemaFile = file(
            "src/commonMain/graphql/xyz/lilsus/papp/data/blink/graphql/schema.graphqls"
        )

        packageName.set("xyz.lilsus.papp.data.blink.graphql")
        schemaFile.set(blinkSchemaFile)
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
        introspection {
            endpointUrl.set("https://api.blink.sv/graphql")
            schemaFile.set(blinkSchemaFile)
        }
    }
}

kover {
    currentProject {
        createVariant("shared") {
            add("android")

            sources {
                includedSourceSets.add("commonMain")
            }
        }
    }

    reports {
        variant("shared") {
            filters {
                excludes {
                    packages(
                        "lasr.shared.generated.resources",
                        "xyz.lilsus.papp.data.blink.graphql",
                        "xyz.lilsus.papp.di"
                    )
                }
            }

            html { onCheck = true }
            xml { onCheck = true }
        }
    }
}
