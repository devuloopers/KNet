plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":logger"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.core)
}
