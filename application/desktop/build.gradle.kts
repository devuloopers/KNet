plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":core:traffic"))
    api(project(":core:scripting"))
    api(project(":core:domain"))
    api(project(":core:connectivity"))
    api(project(":core:companion"))
    api(project(":core:identity"))
    api(project(":core:pairing"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
