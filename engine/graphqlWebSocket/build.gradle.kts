plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":application:desktop"))
    implementation(project(":core:domain"))
    implementation(project(":core:traffic"))
    implementation(project(":engine:protocol"))
    implementation(project(":engine:websocket"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
