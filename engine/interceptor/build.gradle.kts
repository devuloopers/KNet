plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:logger"))
    implementation(project(":engine:proxy"))
    implementation(project(":engine:traffic"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)
    
    testImplementation(project(":engine:certificate"))
    testImplementation(kotlin("test"))
}
