plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":core:domain"))
    api(project(":engine:certificate"))
    api(project(":core:logger"))
    api(project(":core:http"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":testingServer"))
    testImplementation(libs.spring.boot.starter.webflux)
}
