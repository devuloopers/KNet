plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // GraalJS Engine for JavaScript execution
    implementation(libs.graalvm.js)
    implementation(libs.graalvm.js.scriptengine)

    // Kotlin Scripting for JVM Bytecode execution
    implementation(kotlin("scripting-jvm"))
    implementation(kotlin("scripting-compiler-embeddable"))

    testImplementation(kotlin("test"))
}
