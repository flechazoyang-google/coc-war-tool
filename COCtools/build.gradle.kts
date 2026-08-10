plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.cocwar"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cocwar"
        minSdk = 30
        targetSdk = 34
        versionCode = 23
        versionName = "4.4"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM (pinned to offline-cached version)
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.1") // 覆盖 BOM 1.3.0：修复 PullToRefreshBox 指示器不消失（b/343505109）
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Activity + Lifecycle (Compose)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Core (pinned to 1.13.1 to stay compatible with compileSdk 34;
    // Compose BOM 2024.10.01 otherwise pulls 1.15.0 which needs compileSdk 35)
    implementation("androidx.core:core-ktx:1.13.1")

    // Room (local persistence, offline-first)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 单元测试（StatsCalculator 等纯函数逻辑）
    testImplementation("junit:junit:4.13.2")

    // 敏感配置加密存储（WebDAV 密码）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Force core to a compileSdk-34-compatible version (overrides Compose BOM 1.15.0)
    constraints {
        implementation("androidx.core:core-ktx:1.13.1")
        implementation("androidx.core:core:1.13.1")
    }
}
