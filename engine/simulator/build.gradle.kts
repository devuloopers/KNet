import java.time.Duration

plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    api(project(":core:domain"))
    api(project(":application:desktop"))
    implementation(project(":engine:proxy"))
    implementation(libs.netty.all)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(project(":engine:protocol"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

/** Slow real-socket acceptance fixtures are isolated from routine unit tests, with identical test dependencies. */
val qualificationTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}
configurations[qualificationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[qualificationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())
kotlin.target.compilations.getByName(qualificationTest.name).associateWith(kotlin.target.compilations.getByName("main"))

/** Measured one-minute bandwidth and 500 MiB streaming/resource acceptance; never launches a desktop UI. */
tasks.register<Test>("networkProtectionLoadQualification") {
    group = "verification"
    description = "Measures real 100 kbps parallel tunnel delivery and 500 MiB bounded streaming."
    testClassesDirs = qualificationTest.output.classesDirs
    classpath = qualificationTest.runtimeClasspath
    maxHeapSize = "512m"
    maxParallelForks = 1
    timeout.set(Duration.ofMinutes(5))
    systemProperty("io.netty.leakDetection.level", "advanced")
    val metricsDirectory = layout.buildDirectory.dir("reports/network-protection-load")
    outputs.dir(metricsDirectory)
    systemProperty("knet.qualification.metricsDirectory", metricsDirectory.get().asFile.absolutePath)
}
