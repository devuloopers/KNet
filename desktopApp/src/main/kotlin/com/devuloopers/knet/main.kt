package com.devuloopers.knet

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.devuloopers.knet.controller.ProxyStateController
import com.devuloopers.knet.data.repository.KNetCoreRepository
import com.devuloopers.knet.ui.di.initKoin
import java.io.File

fun main() {
    initKoin()
    application {
        val baseDir = remember { File(System.getProperty("user.home"), ".knet") }
        val repository = remember { KNetCoreRepository.getInstance(baseDir) }

        Window(
            onCloseRequest = {
                repository.stopProxy()
                exitApplication()
            },
            title = "KNet",
        ) {
            val scope = rememberCoroutineScope()
            val controller = remember { ProxyStateController(repository, scope) }

            App(controller)
        }
    }
}