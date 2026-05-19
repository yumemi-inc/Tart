pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Koma"
include(":koma-core")
include(":koma-compose")
include(":koma-logging")
include(":koma-message")
include(":koma-test")
