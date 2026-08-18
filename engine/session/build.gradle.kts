plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(project(":application"))
    implementation(libs.kotlinx.coroutines.core)
    
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
