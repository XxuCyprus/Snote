package com.snote.app.di

import android.content.Context
import com.snote.app.data.storage.FileManager
import com.snote.app.data.storage.JsonStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt依赖注入模块 - 提供全局单例对象
 *
 * Hilt是一个依赖注入框架，它帮我们自动管理对象的创建和生命周期。
 * 不需要手动new对象，Hilt会自动注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 提供JsonStorage单例
     */
    @Provides
    @Singleton
    fun provideJsonStorage(): JsonStorage = JsonStorage()

    /**
     * 提供FileManager单例
     */
    @Provides
    @Singleton
    fun provideFileManager(): FileManager = FileManager()
}
