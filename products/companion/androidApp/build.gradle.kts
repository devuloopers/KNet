plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val appVersion: String = providers.gradleProperty("knet.app.version").getOrElse("1.0.0")
val androidVersionCode: Int = providers.gradleProperty("knet.android.versionCode")
    .map { configuredVersionCode ->
        configuredVersionCode.toInt().also { parsedVersionCode ->
            require(parsedVersionCode > 0) { "knet.android.versionCode must be a positive integer." }
        }
    }.getOrElse(1)

val releaseKeystoreFile: String? = providers.environmentVariable("ANDROID_RELEASE_KEYSTORE_FILE").orNull
val releaseKeystorePassword: String? = providers.environmentVariable("ANDROID_RELEASE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias: String? = providers.environmentVariable("ANDROID_RELEASE_KEY_ALIAS").orNull
val releaseSigningAvailable: Boolean = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
).all { configuredValue -> !configuredValue.isNullOrBlank() }

android {
    namespace = "com.devuloopers.knet.companion.android"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.devuloopers.knet.companion"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = androidVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystoreFile))
                storePassword = requireNotNull(releaseKeystorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeystorePassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfigs.findByName("release")?.let { configuredSigning ->
                signingConfig = configuredSigning
            }
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    implementation(project(":products:companion:di"))
    implementation(project(":application:companion"))
    implementation(project(":core:companion"))
    implementation(project(":core:logger"))
    implementation(project(":data:companion"))
    implementation(project(":connectivity:companion"))
    implementation(project(":ui:companion:presentation"))
    implementation(project(":ui:companion:sharedUi"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mlkit.barcode.scanning)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutines.test)
}
