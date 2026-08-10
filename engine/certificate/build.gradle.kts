plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:logger"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    
    testImplementation(kotlin("test"))
}
