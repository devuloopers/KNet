plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    explicitApi()
    jvm()
    android {
        namespace = "com.devuloopers.knet.companion"
        compileSdk = libs.versions.android.compile.sdk.get().toInt()
        minSdk = libs.versions.android.min.sdk.get().toInt()
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:connectivity"))
            api(project(":core:identity"))
            api(project(":core:pairing"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
