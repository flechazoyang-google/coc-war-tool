plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.cocwar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cocwar"
        minSdk = 30
        targetSdk = 35
        versionCode = 25
        versionName = "4.5"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    kotlin {
        jvmToolchain(21)
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // CI 门禁：lint 错误即失败；release 构建不强制（发布走 Gitee Release）
        abortOnError = true
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM (pinned to offline-cached version)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    // Activity + Lifecycle (Compose)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Core
    implementation(libs.core.ktx)

    // Room (local persistence, offline-first)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // JSON parsing
    implementation(libs.gson)

    // Coroutines
    implementation(libs.coroutines.android)

    // 单元测试（StatsCalculator 等纯函数逻辑）
    testImplementation(libs.junit)
}
