plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                api(project(":core:logger"))
                api(project(":core:traffic"))
                api(project(":core:scripting"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.brotli.dec)
                implementation(libs.zstd.jni)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

val generateAppMetadata by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/metadata/kotlin")
    val appName = providers.gradleProperty("knet.app.name").getOrElse("KNet")
    val appVersion = providers.gradleProperty("knet.app.version").getOrElse("1.0.0")
    val appDescription = providers.gradleProperty("knet.app.description").getOrElse("Network Inspector & API Studio")
    val appSuiteName = providers.gradleProperty("knet.app.suiteName").getOrElse("Developer Suite")
    val appPackageId = providers.gradleProperty("knet.app.packageId").getOrElse("com.devuloopers.knet")

    inputs.property("appName", appName)
    inputs.property("appVersion", appVersion)
    inputs.property("appDescription", appDescription)
    inputs.property("appSuiteName", appSuiteName)
    inputs.property("appPackageId", appPackageId)
    outputs.dir(outputDir)

    doLast {
        val file = outputDir.get().file("com/devuloopers/knet/domain/config/AppMetadata.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.devuloopers.knet.domain.config

            /**
             * Auto-generated Application Metadata driven directly by Gradle properties.
             * Single source of truth across all platforms, CI/CD distributions, and UI badges.
             */
            object AppMetadata {
                const val APP_NAME: String = "$appName"
                const val APP_VERSION: String = "$appVersion"
                const val APP_DESCRIPTION: String = "$appDescription"
                const val APP_SUITE_NAME: String = "$appSuiteName"
                const val APP_PACKAGE_ID: String = "$appPackageId"

                val APP_DISPLAY_TITLE: String get() = "$appName $appDescription"
                val APP_VERSION_LABEL: String get() = "$appSuiteName v$appVersion"
            }
            """.trimIndent()
        )
    }
}

kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generateAppMetadata.map { layout.buildDirectory.dir("generated/metadata/kotlin") })

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateAppMetadata)
}
