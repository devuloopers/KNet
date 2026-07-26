plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    
    testImplementation(kotlin("test"))
}
