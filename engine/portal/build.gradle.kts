plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:http"))
    api(project(":engine:certificate"))
    implementation(project(":core:logger"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bouncycastle.prov)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":engine:proxy"))
}
