import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDir("src/jvmMain/kotlin")
            resources.srcDir("src/jvmMain/resources")
        }
        test {
            kotlin.srcDir("src/jvmTest/kotlin")
            resources.srcDir("src/jvmTest/resources")
        }
    }
}

dependencies {
    implementation(project(":ui:desktop:app"))
    implementation(project(":ui:desktop:traffic"))
    implementation(project(":ui:desktop:apistudio"))
    implementation(project(":ui:desktop:certificate"))
    implementation(project(":ui:desktop:httpPanel"))
    implementation(project(":core:domain"))
    implementation(project(":core:logger"))
    implementation(project(":core:serialization"))
    implementation(project(":data:desktop"))
    implementation(project(":storage"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    implementation(libs.compose.uiToolingPreview)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

val appName: String = providers.gradleProperty("knet.app.name").getOrElse("KNet")
val appVersion: String = providers.gradleProperty("knet.app.version").getOrElse("1.0.0")
val appDescription: String = providers.gradleProperty("knet.app.description").getOrElse("Network Inspector & API Studio")
val appPackageId: String = providers.gradleProperty("knet.app.packageId").getOrElse("com.devuloopers.knet")

compose.desktop {
    application {
        mainClass = "com.devuloopers.knet.apps.desktop.MainKt"

        jvmArgs += listOf(
            "-Dapple.awt.application.name=$appName"
        )

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm
            )
            packageName = appName
            packageVersion = appVersion
            description = "$appName $appDescription"

            modules(
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.naming",
                "java.scripting",
                "java.sql",
                "java.security.jgss",
                "jdk.unsupported",
                "jdk.crypto.ec",
                "jdk.crypto.cryptoki"
            )

            macOS {
                iconFile.set(project.file("src/jvmMain/resources/icons/KNet.icns"))
                bundleID = appPackageId
                dockName = appName
            }
            windows {
                iconFile.set(project.file("src/jvmMain/resources/icons/KNet.ico"))
                menuGroup = appName
                upgradeUuid = "6B9CA9A8-D3D6-4B4A-9BE5-1D1C3B4E9F20"
                perUserInstall = false
                shortcut = true
                menu = true
            }
            linux {
                iconFile.set(project.file("src/jvmMain/resources/icons/KNet.png"))
                packageName = appName.lowercase()
                appCategory = "Development;Network;"
                menuGroup = "Development"
                shortcut = true
            }
        }
    }
}
