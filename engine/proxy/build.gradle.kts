plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:traffic"))
    api(project(":core:logger"))
    api(libs.netty.all)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:http"))
    testImplementation(project(":engine:certificate"))
}
