plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":bodyFormatter"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.brotli.dec)
    implementation(libs.zstd.jni)
    api(libs.koin.core)
    testImplementation(kotlin("test"))
}
