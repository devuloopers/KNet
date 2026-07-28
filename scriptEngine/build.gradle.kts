plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Kotlin Scripting for JVM Bytecode execution
    implementation(kotlin("scripting-jvm"))
    implementation(kotlin("scripting-compiler-embeddable"))

    testImplementation(kotlin("test"))
}
