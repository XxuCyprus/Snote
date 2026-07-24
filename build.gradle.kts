// 根项目级别的build.gradle.kts
// 这个文件定义了整个项目使用的插件版本

plugins {
    // Android应用插件 - 用于编译Android应用
    id("com.android.application") version "8.9.0" apply false
    // Kotlin Android插件 - 让我们可以用Kotlin写Android代码
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    // Jetpack Compose编译器插件 - 让Compose UI正常工作
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    // Hilt依赖注入插件 - 管理对象的创建和依赖关系
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    // KSP插件 - 用于处理注解（比kapt更快）
    id("com.google.devtools.ksp") version "2.1.20-1.0.31" apply false
}
