import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    explicitApi()

    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64(),
    )

    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "KNetPacketTunnelRuntime"
            isStatic = true
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.getByName("main") {
            cinterops.create("HevBridge") {
                defFile(project.file("src/nativeInterop/cinterop/HevBridge.def"))
                includeDirs.allHeaders(project.file("src/nativeInterop/cinterop"))
            }
        }
    }

    sourceSets {
        iosTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
