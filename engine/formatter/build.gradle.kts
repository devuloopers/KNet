plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":engine:sse"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.brotli.dec)
    implementation(libs.zstd.jni)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.xml)
    implementation(libs.jackson.dataformat.cbor)
    implementation(libs.jackson.dataformat.msgpack)
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.java.util)
    implementation(libs.graphql.java)

    testImplementation(kotlin("test"))
}
