plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":core:traffic"))
    api(project(":engine:proxy"))

    implementation(project(":core:domain"))
    implementation(project(":application:desktop"))
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.java.util)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.services)
    implementation(libs.grpc.stub)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
