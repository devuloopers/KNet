plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":ui:core"))
                implementation(project(":application:desktop"))
                implementation(project(":core:domain"))
                implementation(project(":core:logger"))
                api(project(":ui:desktop:traffic"))
                api(project(":ui:desktop:connectivity"))
                api(project(":ui:desktop:apiStudio"))
                api(project(":ui:desktop:certificate"))
                api(project(":ui:desktop:breakpointManager"))
                api(project(":ui:desktop:settings"))
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.uiToolingPreview)
                implementation(libs.material.icons.extended)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
