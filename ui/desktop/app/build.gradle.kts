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
                implementation(project(":core:domain"))
                implementation(project(":core:logger"))
                implementation(project(":ui:desktop:workspace"))
                implementation(project(":ui:desktop:apistudio"))
                implementation(project(":ui:desktop:inspector"))
                implementation(project(":ui:desktop:traffic"))
                implementation(project(":ui:desktop:scripting"))
                implementation(project(":ui:desktop:certificate"))
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
                implementation(libs.compose.components.resources)
                implementation(libs.compose.uiToolingPreview)
                implementation(compose.materialIconsExtended)
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
