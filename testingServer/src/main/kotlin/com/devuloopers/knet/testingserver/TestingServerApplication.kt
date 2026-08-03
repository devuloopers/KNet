package com.devuloopers.knet.testingserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
class TestingServerApplication

fun main(args: Array<String>) {
    runApplication<TestingServerApplication>(*args)
}
