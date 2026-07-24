plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":logger"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)

    // Protobuf libraries
    implementation("com.google.protobuf:protobuf-java:3.25.1")
    implementation("com.google.protobuf:protobuf-java-util:3.25.1")

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
}
