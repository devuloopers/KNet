plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}



kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":application:desktop"))
                implementation(project(":core:connectivity"))
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
                implementation(project(":engine:simulator"))
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

/**
 * Wall-clock storage qualification runs after feature suites so concurrent compilation/crypto does not distort
 * its unchanged five-second query budget. The aggregate gate requires this separate report; it is never skipped.
 */
val canonicalTrafficScaleQualification by tasks.registering(Test::class) {
    group = "verification"
    description = "Measures bounded indexed paging over a real 100,000-row SQLite capture."
    val ordinaryTests = tasks.named<Test>("jvmTest")
    dependsOn(ordinaryTests)
    testClassesDirs = ordinaryTests.get().testClassesDirs
    classpath = ordinaryTests.get().classpath
    filter.includeTestsMatching("com.devuloopers.knet.data.desktop.capture.CanonicalTrafficScaleBaselineTest")
    maxParallelForks = 1
}

tasks.named<Test>("jvmTest") {
    filter.excludeTestsMatching("com.devuloopers.knet.data.desktop.capture.CanonicalTrafficScaleBaselineTest")
}
tasks.named("check") { dependsOn(canonicalTrafficScaleQualification) }
