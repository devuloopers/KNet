plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    
    testImplementation(kotlin("test"))
}
