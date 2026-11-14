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
        versionCode = 14
        versionName = "0.5.7-m13"
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
        // 只保留 onnxruntime-android AAR 里的 libonnxruntime.so（带 OrtGetApiBase@VERS_1.28.0）。
        // sherpa 预编译包自带的 1.28.2 运行时导出的是 VERS_1.28.2，Java 绑定解析不了，
        // 所以删掉了那份；sherpa 的 jni/c-api 已把版本需求改标到 VERS_1.28.0（tools/patch-ort-version.py）。
    }
}
