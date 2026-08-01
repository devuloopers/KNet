plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:logger"))
    implementation(project(":storage"))
    implementation(libs.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    
    testImplementation(kotlin("test"))
}
