plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":application:desktop"))
    implementation(project(":core:traffic"))
    api(project(":core:domain"))
    implementation(project(":core:logger"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
