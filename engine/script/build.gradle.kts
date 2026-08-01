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
    api(kotlin("scripting-jvm"))
    api(kotlin("scripting-compiler-embeddable"))
    api(kotlin("scripting-jsr223"))

    testImplementation(kotlin("test"))
}
