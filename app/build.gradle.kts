import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
            implementation("top.yukonga.miuix.kmp:miuix-squircle:0.9.4")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.12.0")
            implementation(project(":core:audio"))
            implementation(project(":core:asr"))
            implementation(project(":core:tts"))
            implementation(project(":core:ocr"))
            implementation(project(":core:model"))
            implementation(project(":core:aac"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
        }
    }
}

android {
    namespace = "io.github.wumisc.woilaibangni"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.wumisc.woilaibangni"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0-m1"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}
