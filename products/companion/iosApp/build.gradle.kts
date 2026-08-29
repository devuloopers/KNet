plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64(),
    )

    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "KNetCompanionIos"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            implementation(project(":products:companion:di"))
            implementation(project(":application:companion"))
            implementation(project(":connectivity:companion"))
            implementation(project(":core:companion"))
            implementation(project(":data:companion"))
            implementation(project(":ui:core"))
            implementation(project(":ui:companion:presentation"))
            implementation(project(":ui:companion:sharedUi"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.devuloopers.knet.companion.ios.generated.resources"
    generateResClass = always
}
