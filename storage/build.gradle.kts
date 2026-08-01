plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            api(libs.datastore.preferences)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain.dependencies {
            // JVM-specific persistence dependencies
        }

        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

dependencies {
    add("kspJvm", libs.room.compiler)
}
