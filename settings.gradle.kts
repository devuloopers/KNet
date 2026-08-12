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

include(":apps:desktop")
project(":apps:desktop").projectDir = file("apps/desktop")
include(":ui:desktop:codeEditor")
project(":ui:desktop:codeEditor").projectDir = file("ui/desktop/codeEditor")
include(":engine:certificate")
project(":engine:certificate").projectDir = file("engine/certificate")
include(":core:http")
project(":core:http").projectDir = file("core/http")
include(":engine:proxy")
project(":engine:proxy").projectDir = file("engine/proxy")
include(":core:logger")
project(":core:logger").projectDir = file("core/logger")
include(":engine:interceptor")
project(":engine:interceptor").projectDir = file("engine/interceptor")
include(":storage")
include(":engine:session")
project(":engine:session").projectDir = file("engine/session")
include(":engine:traffic")
project(":engine:traffic").projectDir = file("engine/traffic")
include(":engine:simulator")
project(":engine:simulator").projectDir = file("engine/simulator")
include(":engine:protocol")
project(":engine:protocol").projectDir = file("engine/protocol")
include(":engine:formatter")
project(":engine:formatter").projectDir = file("engine/formatter")
include(":engine:portal")
project(":engine:portal").projectDir = file("engine/portal")
include(":core:domain")
project(":core:domain").projectDir = file("core/domain")
include(":core:pairing")
project(":core:pairing").projectDir = file("core/pairing")
include(":core:serialization")
project(":core:serialization").projectDir = file("core/serialization")
include(":ui:core")
project(":ui:core").projectDir = file("ui/core")
include(":ui:desktop:app")
project(":ui:desktop:app").projectDir = file("ui/desktop/app")
include(":ui:desktop:workspace")
project(":ui:desktop:workspace").projectDir = file("ui/desktop/workspace")
include(":ui:desktop:apistudio")
project(":ui:desktop:apistudio").projectDir = file("ui/desktop/apistudio")
include(":ui:desktop:traffic")
project(":ui:desktop:traffic").projectDir = file("ui/desktop/traffic")
include(":ui:desktop:http")
project(":ui:desktop:http").projectDir = file("ui/desktop/http")
include(":ui:desktop:scripting")
project(":ui:desktop:scripting").projectDir = file("ui/desktop/scripting")
include(":ui:desktop:certificate")
project(":ui:desktop:certificate").projectDir = file("ui/desktop/certificate")
include(":ui:desktop:settings")
project(":ui:desktop:settings").projectDir = file("ui/desktop/settings")
include(":data:desktop")
project(":data:desktop").projectDir = file("data/desktop")
include(":engine:script")
project(":engine:script").projectDir = file("engine/script")
include(":testingServer")


