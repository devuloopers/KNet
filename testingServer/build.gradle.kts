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

dependencies {
    implementation(kotlin("reflect"))
    implementation(libs.spring.boot.starter.webflux)
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.cbor)
    implementation(libs.jackson.dataformat.msgpack)
    implementation(libs.grpc.netty.shaded)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.services)
    implementation(libs.grpc.stub)
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
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
            plugins {
                id("grpc")
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
