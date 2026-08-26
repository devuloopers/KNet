plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "com.devuloopers.knet.companion.connectivity.android"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    api(project(":application:companion"))
    api(project(":core:companion"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutines.test)
}
