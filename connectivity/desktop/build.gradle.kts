plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    explicitApi()
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":application:desktop"))
                implementation(project(":core:connectivity"))
                implementation(project(":core:companion"))
                implementation(project(":core:identity"))
                implementation(project(":core:logger"))
                implementation(project(":core:traffic"))
                implementation(project(":core:pairing"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.websockets)
                implementation(libs.jmdns)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(libs.bouncycastle.pkix)
                implementation(libs.bouncycastle.prov)
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

/** Loopback/control qualification; the eight real-LAN Wi-Fi fixtures remain in the ordinary jvmTest task. */
tasks.register<Test>("multiDeviceControlQualification") {
    group = "verification"
    description = "Runs desktop control/attribution tests without requiring a physical non-loopback network interface."
    val baseline = tasks.named<Test>("jvmTest")
    testClassesDirs = baseline.get().testClassesDirs
    classpath = baseline.get().classpath
    dependsOn("jvmTestClasses")
    filter {
        listOf(
            "open setup portal serves page and both certificate formats without invitation",
            "open LAN stream is attributed by source address",
            "desktop runtime automatically follows proxy lifecycle",
            "transient gateway ownership recovers without proxy restart",
            "persistent gateway ownership becomes precise failure after fast retries",
            "setup portal conflict selects next configured port",
            "stopping proxy cancels pending Wi-Fi recovery",
            "closing Wi-Fi runtime cancels pending recovery",
        ).forEach { method -> excludeTestsMatching("*.WifiSharingBackendTest.$method*") }
    }
}
