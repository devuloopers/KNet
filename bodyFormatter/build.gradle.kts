plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.brotli.dec)
    implementation(libs.zstd.jni)

    testImplementation(kotlin("test"))
}
