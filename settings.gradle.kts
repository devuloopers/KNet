rootProject.name = "KNet"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":desktopApp")
include(":sharedUI")
include(":certificateManager")
include(":networkEngine")
include(":logger")
include(":interceptor")
include(":storage")
include(":sessionManager")
include(":trafficModifier")
include(":networkSimulator")
include(":protocolInspector")
include(":bodyFormatter")
include(":domain")
include(":data")
include(":scriptEngine")
include(":testingServer")


