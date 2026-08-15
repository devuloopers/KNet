plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                api(project(":core:domain"))
                api(project(":core:logger"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(project(":engine:certificate"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":testingServer"))
                implementation(libs.spring.boot.starter.webflux)
            }
        }
    }
}
