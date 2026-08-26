plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":core:traffic"))
    api(project(":engine:proxy"))

    implementation(project(":core:domain"))
    implementation(project(":application:desktop"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
