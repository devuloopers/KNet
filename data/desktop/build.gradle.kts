plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":application"))
                implementation(project(":core:domain"))
                implementation(project(":core:logger"))
                implementation(project(":core:identity"))
                implementation(project(":core:pairing"))
                implementation(project(":core:serialization"))
                implementation(project(":core:traffic"))
                api(project(":core:http"))
                api(project(":storage"))

                api(libs.room.runtime)
                api(libs.sqlite.bundled)

                implementation(project(":engine:proxy"))
                implementation(project(":engine:certificate"))
                implementation(project(":engine:session"))
                implementation(project(":engine:interceptor"))
                implementation(project(":engine:protocol"))
                implementation(project(":engine:formatter"))
                implementation(project(":engine:script"))
                implementation(libs.netty.all)

                implementation(libs.datastore.preferences)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(project(":engine:sse"))
            }
        }
    }
}
