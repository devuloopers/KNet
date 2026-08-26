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
