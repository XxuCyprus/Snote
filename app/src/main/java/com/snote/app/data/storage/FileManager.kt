package com.snote.app.data.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 文件管理器 - 负责多媒体文件的复制和管理
 *
 * 当用户导入图片、视频、音频等文件时，此管理器将其复制到
 * 数据目录下对应的笔记本文件夹中。
 *
 * 目录结构示例：
 *   Documents/Snote/
 *   ├── snote_data.json
 *   ├── covers/
 *   │   └── notebook_1.jpg
 *   └── notebook_1/
 *       ├── images/
 *       │   └── img_xxx.jpg
 *       ├── videos/
 *       │   └── video_xxx.mp4
 *       └── audio/
 *           └── audio_xxx.mp3
 */
class FileManager {

    /**
     * 将外部文件复制到笔记本的对应目录中
     *
     * @param context Android上下文
     * @param sourceUri 源文件的URI（来自相册、文件选择器等）
     * @param dataDir 数据根目录
     * @param notebookId 笔记本ID（用作文件夹名）
     * @param contentType 内容类型（决定存放的子目录）
     * @return 复制后的文件相对路径，失败返回null
     */
    fun copyFileToNotebook(
        context: Context,
        sourceUri: Uri,
        dataDir: File,
        notebookId: String,
        contentType: String
    ): String? {
        return try {
            val subDir = when (contentType) {
                "IMAGE" -> "images"
                "VIDEO" -> "videos"
                "AUDIO" -> "audio"
                else -> "files"
            }

            val targetDir = File(dataDir, "$notebookId/$subDir")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val extension = getFileExtension(context, sourceUri)
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "${contentType.lowercase()}_$timestamp$extension"
            val targetFile = File(targetDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 返回相对路径
            "$notebookId/$subDir/$fileName"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 保存笔记本封面图片
     */
    fun saveCoverImage(
        context: Context,
        sourceUri: Uri,
        dataDir: File,
        notebookId: String
    ): String? {
        return try {
            val coversDir = File(dataDir, "covers")
            if (!coversDir.exists()) {
                coversDir.mkdirs()
            }

            val extension = getFileExtension(context, sourceUri)
            val fileName = "${notebookId}$extension"
            val targetFile = File(coversDir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            "covers/$fileName"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 删除文件
     */
    fun deleteFile(dataDir: File, relativePath: String): Boolean {
        val file = File(dataDir, relativePath)
        val mainDeleted = if (file.exists()) file.delete() else true
        // 清理图片编辑伴生文件
        File(dataDir, "$relativePath.strokes").let { if (it.exists()) it.delete() }
        File(dataDir, "$relativePath.base").let { if (it.exists()) it.delete() }
        File(dataDir, "$relativePath.doodles.json").let { if (it.exists()) it.delete() }
        File(dataDir, "${relativePath}_clean.jpg").let { if (it.exists()) it.delete() }
        return mainDeleted
    }

    /**
     * 删除笔记本的所有文件
     */
    fun deleteNotebookFiles(dataDir: File, notebookId: String): Boolean {
        val notebookDir = File(dataDir, notebookId)
        return if (notebookDir.exists()) notebookDir.deleteRecursively() else true
    }

    /**
     * 获取文件扩展名
     */
    private fun getFileExtension(context: Context, uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("jpeg") == true -> ".jpg"
            mimeType?.contains("png") == true -> ".png"
            mimeType?.contains("gif") == true -> ".gif"
            mimeType?.contains("webp") == true -> ".webp"
            mimeType?.contains("mp4") == true -> ".mp4"
            mimeType?.contains("mp3") == true -> ".mp3"
            mimeType?.contains("wav") == true -> ".wav"
            mimeType?.contains("pdf") == true -> ".pdf"
            else -> {
                // 从URI路径获取扩展名
                val path = uri.path ?: return ".bin"
                val dotIndex = path.lastIndexOf('.')
                if (dotIndex >= 0) path.substring(dotIndex) else ".bin"
            }
        }
    }
}
