plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":core:domain"))
                implementation(project(":core:logger"))
                implementation(project(":core:serialization"))
                api(project(":core:http"))
                api(project(":storage"))

                api(libs.room.runtime)
                api(libs.sqlite.bundled)

                implementation(project(":engine:proxy"))
                implementation(project(":engine:certificate"))
                implementation(project(":engine:session"))
                implementation(project(":engine:traffic"))
                implementation(project(":engine:interceptor"))
                implementation(project(":engine:portal"))
                implementation(project(":engine:protocol"))
                implementation(libs.netty.all)

                implementation(libs.datastore.preferences)
                implementation(libs.kotlinx.coroutines.core)
                api(libs.koin.core)
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
