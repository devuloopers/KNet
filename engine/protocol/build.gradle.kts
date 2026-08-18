plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":application"))
    implementation(project(":core:traffic"))
    api(project(":core:domain"))
    implementation(project(":core:logger"))
    implementation(libs.kotlinx.coroutines.core)

    // Jackson JSON parser for GraphQL inspection
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
}
