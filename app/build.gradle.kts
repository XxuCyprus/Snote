import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.snote.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.snote.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 15
        versionName = "5.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("snote-release.jks")
            storePassword = localProperties.getProperty("storePassword")
            keyAlias = "Snote"
            keyPassword = localProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output.outputFileName = "Snote-v${versionName}.apk"
        }
    }
}

dependencies {
    // ========== Jetpack Compose (UI框架) ==========
    // Compose BOM - 统一管理Compose组件版本
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)

    // Material 3 - Material Design 3 组件库（按钮、卡片、文本框等）
    implementation("androidx.compose.material3:material3")
    // Material Icons Extended - 更多图标
    implementation("androidx.compose.material:material-icons-extended")
    // Compose UI工具 - 预览等功能
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ========== Android核心库 ==========
    // Activity Compose - 在Compose中使用Activity
    implementation("androidx.activity:activity-compose:1.9.3")
    // RecyclerView - 原生列表组件
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Lifecycle - 管理应用生命周期
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // Navigation Compose - 页面导航
    implementation("androidx.navigation:navigation-compose:2.8.5")
    // Core KTX - Kotlin扩展函数
    implementation("androidx.core:core-ktx:1.15.0")

    // ========== Hilt (依赖注入) ==========
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ========== Coil (图片加载) ==========
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // ========== JSON处理 ==========
    // Gson - Google的JSON序列化/反序列化库
    implementation("com.google.code.gson:gson:2.11.0")

    // ========== 数据存储 ==========
    // DataStore - 用于存储简单的键值对数据（如设置项）
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ========== Imaging 图片编辑库 ==========
    implementation(project(":image"))
}
