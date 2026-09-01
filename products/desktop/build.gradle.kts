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
    implementation(project(":application:desktop"))
    implementation(project(":ui:desktop:app"))
    implementation(project(":ui:desktop:traffic"))
    implementation(project(":ui:desktop:connectivity"))
    implementation(project(":ui:desktop:apiStudio"))
    implementation(project(":ui:desktop:apiStudio:grpc"))
    implementation(project(":ui:desktop:apiStudio:graphqlWebSocket"))
    implementation(project(":ui:desktop:apiStudio:websocket"))
    implementation(project(":ui:desktop:certificate"))
    implementation(project(":ui:desktop:breakpointManager"))
    implementation(project(":ui:desktop:httpPanel"))
    implementation(project(":ui:desktop:settings"))
    implementation(project(":core:domain"))
    implementation(project(":core:http"))
    implementation(project(":core:logger"))
    implementation(project(":core:serialization"))
    implementation(project(":core:traffic"))
    implementation(project(":data:desktop"))
    implementation(project(":connectivity:desktop"))
    implementation(project(":engine:certificate"))
    implementation(project(":engine:formatter"))
    implementation(project(":engine:grpc"))
    implementation(project(":engine:graphqlWebSocket"))
    implementation(project(":engine:websocket"))
    implementation(project(":engine:protocol"))
    implementation(project(":engine:proxy"))
    implementation(project(":engine:session"))
    implementation(project(":engine:sse"))
    implementation(project(":storage"))
    implementation(project(":ui:core"))

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.compose.uiToolingPreview)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:companion"))
}

val appName: String = providers.gradleProperty("knet.app.name").getOrElse("KNet")
val appVersion: String = providers.gradleProperty("knet.desktop.version").getOrElse("0.1.4")
val appDescription: String = providers.gradleProperty("knet.app.description").getOrElse("Network Inspector & API Studio")
val appPackageId: String = providers.gradleProperty("knet.app.packageId").getOrElse("com.devuloopers.knet")
val nativePackageVersion: String = providers.gradleProperty("knet.desktop.packageVersion").getOrElse(
    appVersion.toJPackageVersion(isMacOS = System.getProperty("os.name").startsWith("Mac", ignoreCase = true))
)

compose.desktop {
    application {
        mainClass = "com.devuloopers.knet.products.desktop.MainKt"

        jvmArgs += listOf(
            "-Dapple.awt.application.name=$appName",
            "-Dsun.java2d.d3d=true",
            "-Dsun.java2d.noddraw=true",
            "-Dsun.java2d.opengl=false"
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
            packageVersion = nativePackageVersion
            description = "$appName $appDescription"

            modules(
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.naming",
                "java.net.http",
                "java.scripting",
                "java.sql",
                "java.security.jgss",
                "jdk.unsupported",
                "jdk.httpserver",
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
                debMaintainer = "Devuloopers <devuloopers@gmail.com>"
                appRelease = "1"
                rpmLicenseType = "Apache-2.0"
                shortcut = true
            }
        }
    }
}

/**
 * Converts the public SemVer into the strictly numeric version accepted by jpackage.
 * The application still exposes [appVersion]; this value is package metadata only.
 * macOS requires a positive first component, so pre-1.0 releases use a packaging epoch of 1.
 */
private fun String.toJPackageVersion(isMacOS: Boolean): String {
    val coreVersion = substringBefore('-').substringBefore('+')
    val components = coreVersion.split('.')
    require(components.size == 3 && components.all { component -> component.toUIntOrNull() != null }) {
        "Desktop package version must be semantic versioning with three numeric components: $this"
    }

    val major = components[0].toUInt()
    return if (isMacOS && major == 0u) {
        "1.${components[1]}.${components[2]}"
    } else {
        coreVersion
    }
}
