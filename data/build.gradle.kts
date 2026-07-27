plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":networkEngine"))
    implementation(project(":certificateManager"))
    implementation(project(":sessionManager"))
    implementation(project(":storage"))
    implementation(project(":interceptor"))
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    api(libs.koin.core)

    testImplementation(kotlin("test"))
}
