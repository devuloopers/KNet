import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(project(":core:traffic"))
    api(project(":engine:proxy"))

    implementation(project(":application"))
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.named<Test>("test") {
    exclude("**/SseReleaseSoakTest.class")
}

/** Explicit multi-hour SSE codec churn gate; intentionally excluded from the ordinary test task. */
tasks.register<Test>("sseReleaseSoak") {
    group = "verification"
    description = "Runs the configurable long-duration SSE content-codec churn qualification."
    testClassesDirs = tasks.named<Test>("test").get().testClassesDirs
    classpath = tasks.named<Test>("test").get().classpath
    filter.includeTestsMatching("*SseReleaseSoakTest")
    systemProperty(
        "knet.sse.soak.seconds",
        providers.gradleProperty("knet.sse.soak.seconds").orElse("10800").get(),
    )
}
