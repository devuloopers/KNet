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
                implementation(project(":application"))
                implementation(project(":core:connectivity"))
                implementation(project(":core:traffic"))
                implementation(project(":core:pairing"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
