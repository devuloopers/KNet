plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:logger"))
    implementation(libs.netty.all)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
}
