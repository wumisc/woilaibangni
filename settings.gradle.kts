pluginManagement {
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

rootProject.name = "woilaibangni"

include(":app")
include(":core:audio")
include(":core:asr")
include(":core:tts")
include(":core:ocr")
include(":core:model")
include(":core:aac")
