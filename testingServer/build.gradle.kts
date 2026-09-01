import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
    alias(libs.plugins.kotlinSpring)
    alias(libs.plugins.protobuf)
}

val dependencyVersions = versionCatalogs.named("libs")
val protobufVersion: String = dependencyVersions.findVersion("protobuf").get().requiredVersion
val grpcVersion: String = dependencyVersions.findVersion("grpc").get().requiredVersion
val coroutinesVersion: String = dependencyVersions.findVersion("kotlinx-coroutines").get().requiredVersion

// Spring Boot manages coroutines transitively. Override its older BOM value so core, Reactor, and test artifacts
// resolve to the repository's single version instead of producing binary-incompatible runtime combinations.
extra["kotlin-coroutines.version"] = coroutinesVersion
// Generated protobuf classes reject an older runtime. Keep Spring Boot's managed runtime aligned with protoc.
extra["protobuf-java.version"] = protobufVersion

dependencies {
    implementation(kotlin("reflect"))
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.graphql)
    implementation(libs.kotlinx.coroutines.reactor)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.cbor)
    implementation(libs.jackson.dataformat.msgpack)
    implementation(libs.netty.all)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.services)
    implementation(libs.grpc.stub)
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation(kotlin("test"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
    }
    generateProtoTasks {
        all().configureEach {
            // protobuf-gradle-plugin 0.10.0 still retains Gradle model objects on these tasks. Gradle's targeted
            // opt-out keeps configuration caching enabled for the rest of KNet while safely discarding entries
            // for builds that execute protobuf generation.
            notCompatibleWithConfigurationCache(
                "protobuf-gradle-plugin 0.10.0 GenerateProtoTask is not compatible with Gradle 9 configuration cache",
            )
            plugins {
                maybeCreate("grpc")
            }
        }
    }
}

springBoot {
    mainClass.set("com.devuloopers.knet.testingserver.TestingServerApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}
