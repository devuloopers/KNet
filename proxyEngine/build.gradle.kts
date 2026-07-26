plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":certificateManager"))
    implementation(project(":logger"))
    implementation(libs.netty.all)
    
    testImplementation(kotlin("test"))
}
