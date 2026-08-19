plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":application"))
    implementation(project(":core:domain"))
    implementation(project(":core:logger"))
    implementation(project(":engine:proxy"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)
    
    testImplementation(project(":engine:certificate"))
    testImplementation(project(":engine:protocol"))
    testImplementation(kotlin("test"))
}
