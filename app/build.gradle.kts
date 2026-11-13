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
            implementation(project(":core:log"))
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
        versionCode = 9
        versionName = "0.5.2-m8"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // 两种发行形态：slim 走按需下载模型；full 把标准 zipformer + PP-OCRv5 内置进 APK。
    // 差异只在 assets，代码里通过 BundledModels.hasBundled() 自动判断。
    flavorDimensions += "dist"
    productFlavors {
        create("slim") {
            dimension = "dist"
            versionNameSuffix = ""
        }
        create("full") {
            dimension = "dist"
            versionNameSuffix = "-full"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        // sherpa-onnx 的预编译包与 onnxruntime-android AAR 都带 libonnxruntime.so，
        // 两者版本已对齐（1.28.2），保留任意一份即可
        jniLibs.pickFirsts += setOf("**/libonnxruntime.so")
    }
}
