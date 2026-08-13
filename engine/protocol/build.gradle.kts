plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:logger"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)

    // Jackson JSON parser for GraphQL inspection
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Protobuf libraries
    implementation("com.google.protobuf:protobuf-java:3.25.1")
    implementation("com.google.protobuf:protobuf-java-util:3.25.1")

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
}
