plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":core:logger"))
    implementation(libs.kotlinx.datetime)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    
    testImplementation(kotlin("test"))
}
