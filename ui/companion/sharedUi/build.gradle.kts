plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    explicitApi()
    jvm()
    android {
        namespace = "com.devuloopers.knet.companion.sharedui"
        compileSdk = libs.versions.android.compile.sdk.get().toInt()
        minSdk = libs.versions.android.min.sdk.get().toInt()
        androidResources.enable = true
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":ui:companion:presentation"))
            implementation(project(":core:companion"))
            implementation(project(":ui:core"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        iosMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.devuloopers.knet.companion.sharedui.generated.resources"
    generateResClass = always
}
