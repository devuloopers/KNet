plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":logger"))
    implementation(project(":networkEngine"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)
    
    testImplementation(project(":certificateManager"))
    testImplementation(kotlin("test"))
}
