package com.snote.app.data.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一管理所有模块的数据目录和权限检查
 *
 * Notes、Todo、Countdown、StudyTime 四个模块共用同一个根目录，
 * 通过此单例确保降级逻辑一致。
 */
@Singleton
class AppDataManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "AppDataManager"

    /**
     * 统一的数据根目录（getter，每次调用重新计算以适应权限变化）
     * 有权限: Documents/Snote/ (外部存储，卸载后保留)
     * 无权限: context.filesDir/Snote/ (内部存储，卸载后丢失)
     */
    val dataDir: File
        get() {
            return if (hasFullStorageAccess()) {
                try {
                    val documentsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS
                    )
                    File(documentsDir, "Snote")
                } catch (e: Exception) {
                    Log.e(TAG, "获取外部存储路径失败，降级到内部存储", e)
                    File(context.filesDir, "Snote")
                }
            } else {
                Log.w(TAG, "无文件访问权限，使用内部存储")
                File(context.filesDir, "Snote")
            }
        }

    /**
     * 内部存储备份目录（权限撤销时使用）
     */
    val internalDataDir: File
        get() = File(context.filesDir, "Snote")

    /**
     * 检查是否有完整文件访问权限（API 30+）
     */
    fun hasFullStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * 确保有写入权限，如果没有则抛出异常
     * 在所有写入操作前调用
     */
    fun ensureWritePermission() {
        if (!hasFullStorageAccess()) {
            throw SecurityException("没有文件访问权限，无法写入数据")
        }
    }

    /**
     * 获取数据目录的绝对路径字符串
     */
    fun getDataDirPath(): String = dataDir.absolutePath

    /**
     * 获取文件的绝对路径
     */
    fun getAbsolutePath(relativePath: String): File {
        return File(dataDir, relativePath)
    }
}
