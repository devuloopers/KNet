package com.devuloopers.knet.testingserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * Starts the deterministic local protocol lab used to exercise KNet capture and inspection.
 *
 * The Spring application owns HTTP-family fixtures while independently managed lifecycle beans
 * may bind additional protocol listeners, such as the native gRPC endpoint.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class TestingServerApplication

/**
 * Launches the local protocol lab.
 *
 * @param args Spring Boot command-line configuration arguments.
 */
fun main(args: Array<String>) {
    runApplication<TestingServerApplication>(*args)
}
