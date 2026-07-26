package com.snote.app.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.snote.app.data.model.*
import com.snote.app.data.storage.FileManager
import com.snote.app.data.storage.JsonStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonStorage: JsonStorage,
    private val fileManager: FileManager
) {
    private val TAG = "SnoteRepo"
    private var data = SnoteData()
    private var dataDir: File = getDefaultDataDir()
    private var isInitialized = false
    private var dataFileName = "snote_data.json"

    /**
     * 获取默认数据目录：Documents/Snote/
     * 如果外部存储不可用，降级到应用内部存储
     */
    private fun getDefaultDataDir(): File {
        return try {
            val state = Environment.getExternalStorageState()
            if (Environment.MEDIA_MOUNTED == state) {
                val documentsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS
                )
                File(documentsDir, "Snote")
            } else {
                Log.w(TAG, "外部存储不可用(state=$state)，使用内部存储")
                File(context.filesDir, "Snote")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取外部存储路径失败，降级到内部存储", e)
            File(context.filesDir, "Snote")
        }
    }

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
     * 重置初始化标记 + 数据目录，允许下次 initializeDataDir 重新加载数据。
     * 用于权限变更后强制重新扫描数据目录。
     */
    fun reinitialize() {
        isInitialized = false
        dataDir = getDefaultDataDir()
        dataFileName = "snote_data.json"
    }

    /**
     * 初始化数据目录
     */
    suspend fun initializeDataDir() = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "已初始化，跳过")
            return@withContext
        }
        isInitialized = true

        // 无权限 + 外部有旧数据 → 用 pending 文件名，不覆盖原 snote_data.json
        if (!hasFullStorageAccess() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mainFile = File(dataDir, "snote_data.json")
            if (mainFile.exists() && mainFile.length() > 0) {
                Log.w(TAG, "检测到旧数据但无权限，使用 snote_data_pending.json")
                dataFileName = "snote_data_pending.json"
            }
        }

        Log.d(TAG, "数据目录: ${dataDir.absolutePath}, 文件: $dataFileName")
        Log.d(TAG, "目录存在: ${dataDir.exists()}")

        if (!dataDir.exists()) {
            val created = dataDir.mkdirs()
            Log.d(TAG, "创建目录结果: $created")
        }

        data = jsonStorage.loadData(dataDir, dataFileName)
        Log.d(TAG, "加载完成, 笔记本数: ${data.notebooks.size}")
    }

    /**
     * 权限刚被授予 → 把 pending 文件的数据合并到主文件，删除 pending
     */
    suspend fun mergeAndSwitchToExternal() = withContext(Dispatchers.IO) {
        if (!hasFullStorageAccess()) return@withContext

        val pendingFile = File(dataDir, "snote_data_pending.json")
        val mainFile = File(dataDir, "snote_data.json")

        if (!pendingFile.exists() || pendingFile.length() == 0L) {
            // 没有 pending 文件，直接切回主文件名
            dataFileName = "snote_data.json"
            isInitialized = false
            return@withContext
        }

        // 读取旧主文件和 pending 文件
        val mainData = try {
            jsonStorage.loadData(dataDir, "snote_data.json")
        } catch (_: Exception) { SnoteData() }

        val pendingData = try {
            jsonStorage.loadData(dataDir, "snote_data_pending.json")
        } catch (_: Exception) { SnoteData() }

        // 合并：主文件数据优先，pending 补充不存在的
        val mergedNotebooks = mainData.notebooks.toMutableList()
        for (nb in pendingData.notebooks) {
            if (mergedNotebooks.none { it.id == nb.id }) {
                mergedNotebooks.add(nb)
            }
        }

        // 保存合并结果到主文件
        val merged = SnoteData(notebooks = mergedNotebooks)
        jsonStorage.saveData(dataDir, merged, "snote_data.json")

        // 删除 pending 文件
        pendingFile.delete()
        Log.d(TAG, "pending 已合并到主文件并删除")

        // 切回主文件名
        dataFileName = "snote_data.json"
        data = merged
        isInitialized = false
    }

    /**
     * 扫描文件系统所有目录，恢复 JSON 中丢失或内容为空的笔记本。
     * 不管 JSON 里有没有引用，只要目录下有媒体文件就恢复。
     *
     * @return 恢复/补全的笔记本数量
     */
    suspend fun scanAndRecoverOrphanedData(): Int = withContext(Dispatchers.IO) {
        val existingNotebooks = data.notebooks.toMutableList()
        var changed = false

        // 扫描 dataDir 所有子目录（跳过 covers）
        val allDirs = (dataDir.listFiles() ?: emptyArray()).filter { it.isDirectory && it.name != "covers" }
        val existingIds = existingNotebooks.map { it.id }.toSet()

        for (dir in allDirs) {
            val notebookId = dir.name
            val items = mutableListOf<ContentItem>()
            scanMediaDir(dir, "images", ContentType.IMAGE, items)
            scanMediaDir(dir, "videos", ContentType.VIDEO, items)
            scanMediaDir(dir, "audio", ContentType.AUDIO, items)
            scanMediaDir(dir, "files", ContentType.FILE, items)

            if (items.isEmpty()) continue

            val existingNotebook = existingNotebooks.find { it.id == notebookId }
            if (existingNotebook != null) {
                // JSON 里有这个笔记本但内容可能是空的 → 补全
                if (existingNotebook.chapters.isEmpty() || existingNotebook.chapters.all { it.items.isEmpty() }) {
                    val chapter = Chapter(
                        title = "已恢复内容", level = 1, order = 0,
                        items = items.mapIndexed { idx, item -> item.copy(order = idx) }
                    )
                    val restored = existingNotebook.copy(chapters = listOf(chapter))
                    val idx = existingNotebooks.indexOfFirst { it.id == notebookId }
                    if (idx >= 0) existingNotebooks[idx] = restored
                    changed = true
                    Log.d(TAG, "补全已有笔记本: $notebookId, 文件数: ${items.size}")
                }
            } else {
                // JSON 里没有 → 新建恢复笔记本
                val chapter = Chapter(
                    title = "已恢复内容", level = 1, order = 0,
                    items = items.mapIndexed { idx, item -> item.copy(order = idx) }
                )
                val notebook = Notebook(
                    id = notebookId,
                    title = "已恢复笔记本 ($notebookId)",
                    description = "从文件系统恢复，原标题已丢失",
                    chapters = listOf(chapter)
                )
                existingNotebooks.add(notebook)
                changed = true
                Log.d(TAG, "新建恢复笔记本: $notebookId, 文件数: ${items.size}")
            }
        }

        if (!changed) return@withContext 0

        data = data.copy(notebooks = existingNotebooks)
        save()
        Log.d(TAG, "恢复完成, 总笔记本数: ${existingNotebooks.size}")
        existingNotebooks.size - existingIds.size + (if (changed) 1 else 0) // approximate
    }

    /** 扫描指定媒体目录，将文件转为 ContentItem */
    private fun scanMediaDir(
        notebookDir: File, subDirName: String, type: ContentType, items: MutableList<ContentItem>
    ) {
        val subDir = File(notebookDir, subDirName)
        if (!subDir.exists() || !subDir.isDirectory) return

        val files = subDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        for (file in files) {
            if (file.isFile) {
                val relativePath = file.absolutePath.removePrefix("${dataDir.absolutePath}/")
                items.add(ContentItem(type = type, content = relativePath, order = items.size))
            }
        }
    }

    /**
     * 获取数据目录路径（用于设置页面显示）
     */
    fun getDataDirPath(): String = dataDir.absolutePath

    // ==================== 辅助方法：copy-on-write 树操作 ====================

    /** 对指定 ID 的笔记本应用 transform */
    private fun withNotebook(notebookId: String, transform: (Notebook) -> Notebook) {
        data = data.copy(
            notebooks = data.notebooks.map { nb ->
                if (nb.id == notebookId) transform(nb) else nb
            }
        )
    }

    /** 在 Notebook 的章节树中递归查找并更新指定章节 */
    private fun Notebook.withChapter(targetId: String, transform: (Chapter) -> Chapter): Notebook {
        return copy(chapters = updateChapterRecursive(chapters, targetId, transform))
    }

    /** 递归在章节列表中查找指定 ID 并应用 transform */
    private fun updateChapterRecursive(
        chapters: List<Chapter>,
        targetId: String,
        transform: (Chapter) -> Chapter
    ): List<Chapter> {
        return chapters.map { ch ->
            if (ch.id == targetId) transform(ch)
            else ch.copy(children = updateChapterRecursive(ch.children, targetId, transform))
        }
    }

    /** 从章节列表中递归删除指定 ID 的章节，返回新列表 */
    private fun removeChapterFromList(chapters: List<Chapter>, chapterId: String): List<Chapter> {
        return chapters.filterNot { it.id == chapterId }
            .map { ch -> ch.copy(children = removeChapterFromList(ch.children, chapterId)) }
    }

    /**
     * 在笔记本中查找章节（递归搜索）
     */
    private fun findChapter(chapters: List<Chapter>, chapterId: String): Chapter? {
        for (chapter in chapters) {
            if (chapter.id == chapterId) return chapter
            val found = findChapter(chapter.children, chapterId)
            if (found != null) return found
        }
        return null
    }

    /** 在指定笔记本中查找章节 */
    fun findChapterInNotebook(notebookId: String, chapterId: String): Chapter? {
        val notebook = data.notebooks.find { it.id == notebookId } ?: return null
        return findChapter(notebook.chapters, chapterId)
    }

    /** 查找某章节的父章节（不包含顶层无父章节的情况） */
    private fun findParent(chapters: List<Chapter>, childId: String): Chapter? {
        for (chapter in chapters) {
            if (chapter.children.any { it.id == childId }) return chapter
            val found = findParent(chapter.children, childId)
            if (found != null) return found
        }
        return null
    }

    /** 删除章节关联的所有文件 */
    private fun deleteChapterFiles(chapter: Chapter) {
        for (item in chapter.items) {
            if (item.type != ContentType.TEXT) {
                fileManager.deleteFile(dataDir, item.content)
            }
        }
        for (child in chapter.children) {
            deleteChapterFiles(child)
        }
    }

    /**
     * 保存数据到文件
     */
    private suspend fun save() = withContext(Dispatchers.IO) {
        jsonStorage.saveData(dataDir, data, dataFileName)
    }

    // ==================== 笔记本操作 ====================

    /**
     * 获取所有笔记本列表
     */
    fun getAllNotebooks(): List<Notebook> = data.notebooks

    /**
     * 根据ID获取笔记本
     */
    fun getNotebookById(id: String): Notebook? {
        return data.notebooks.find { it.id == id }
    }

    /**
     * 创建新笔记本
     */
    suspend fun createNotebook(title: String, description: String = ""): Notebook = withContext(Dispatchers.IO) {
        val notebook = Notebook(title = title, description = description)
        data = data.copy(notebooks = data.notebooks + notebook)
        val notebookDir = File(dataDir, notebook.id)
        if (!notebookDir.exists()) notebookDir.mkdirs()
        save()
        notebook
    }

    /**
     * 更新笔记本信息
     */
    suspend fun updateNotebook(notebook: Notebook) = withContext(Dispatchers.IO) {
        withNotebook(notebook.id) { notebook }
        save()
    }

    /**
     * 删除笔记本（包括所有章节、内容和文件）
     */
    suspend fun deleteNotebook(notebookId: String) = withContext(Dispatchers.IO) {
        data = data.copy(notebooks = data.notebooks.filterNot { it.id == notebookId })
        fileManager.deleteNotebookFiles(dataDir, notebookId)
        save()
    }

    // ==================== 章节操作 ====================

    /**
     * 在笔记本中添加顶级章节（一级标题）
     */
    suspend fun addChapter(notebookId: String, title: String): Chapter? = withContext(Dispatchers.IO) {
        val notebook = data.notebooks.find { it.id == notebookId } ?: return@withContext null
        val chapter = Chapter(title = title, level = 1, order = notebook.chapters.size)
        withNotebook(notebookId) { nb -> nb.copy(chapters = nb.chapters + chapter) }
        save()
        chapter
    }

    /**
     * 在指定章节下添加子章节
     */
    suspend fun addChildChapter(notebookId: String, parentChapterId: String, title: String): Chapter? = withContext(Dispatchers.IO) {
        val notebook = data.notebooks.find { it.id == notebookId } ?: return@withContext null
        val parentChapter = findChapter(notebook.chapters, parentChapterId) ?: return@withContext null

        if (parentChapter.level >= 6) return@withContext null

        val child = Chapter(
            title = title,
            level = parentChapter.level + 1,
            order = parentChapter.children.size
        )
        withNotebook(notebookId) { nb ->
            nb.withChapter(parentChapterId) { ch ->
                ch.copy(children = ch.children + child)
            }
        }
        save()
        child
    }

    /**
     * 更新章节标题
     */
    suspend fun updateChapter(notebookId: String, chapter: Chapter) = withContext(Dispatchers.IO) {
        val notebook = data.notebooks.find { it.id == notebookId } ?: return@withContext
        val existing = findChapter(notebook.chapters, chapter.id) ?: return@withContext
        withNotebook(notebookId) { nb ->
            nb.withChapter(chapter.id) { ch ->
                chapter.copy(children = ch.children, items = ch.items)
            }
        }
        save()
    }

    /**
     * 删除章节（包括所有子章节和内容）
     */
    suspend fun deleteChapter(notebookId: String, chapterId: String) = withContext(Dispatchers.IO) {
        val notebook = data.notebooks.find { it.id == notebookId } ?: return@withContext
        val chapter = findChapter(notebook.chapters, chapterId)
        if (chapter != null) {
            deleteChapterFiles(chapter)
        }
        withNotebook(notebookId) { nb ->
            nb.copy(chapters = removeChapterFromList(nb.chapters, chapterId))
        }
        save()
    }

    // ==================== 内容条目操作 ====================

    /**
     * 添加文字内容
     */
    suspend fun addTextContent(notebookId: String, chapterId: String, text: String): ContentItem? = withContext(Dispatchers.IO) {
        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext null
        val item = ContentItem(type = ContentType.TEXT, content = text, order = chapter.items.size)
        withNotebook(notebookId) { nb ->
            nb.withChapter(chapterId) { ch -> ch.copy(items = ch.items + item) }
        }
        save()
        item
    }

    /**
     * 添加图片内容
     */
    suspend fun addImageContent(notebookId: String, chapterId: String, imageUri: android.net.Uri): ContentItem? = withContext(Dispatchers.IO) {
        val relativePath = fileManager.copyFileToNotebook(context, imageUri, dataDir, notebookId, "IMAGE")
            ?: return@withContext null

        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext null
        val item = ContentItem(type = ContentType.IMAGE, content = relativePath, order = chapter.items.size)
        withNotebook(notebookId) { nb ->
            nb.withChapter(chapterId) { ch -> ch.copy(items = ch.items + item) }
        }
        save()
        item
    }

    /**
     * 添加视频内容
     */
    suspend fun addVideoContent(notebookId: String, chapterId: String, videoUri: android.net.Uri): ContentItem? = withContext(Dispatchers.IO) {
        val relativePath = fileManager.copyFileToNotebook(context, videoUri, dataDir, notebookId, "VIDEO")
            ?: return@withContext null

        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext null
        val item = ContentItem(type = ContentType.VIDEO, content = relativePath, order = chapter.items.size)
        withNotebook(notebookId) { nb ->
            nb.withChapter(chapterId) { ch -> ch.copy(items = ch.items + item) }
        }
        save()
        item
    }

    /**
     * 添加音频内容
     */
    suspend fun addAudioContent(notebookId: String, chapterId: String, audioUri: android.net.Uri): ContentItem? = withContext(Dispatchers.IO) {
        val relativePath = fileManager.copyFileToNotebook(context, audioUri, dataDir, notebookId, "AUDIO")
            ?: return@withContext null

        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext null
        val item = ContentItem(type = ContentType.AUDIO, content = relativePath, order = chapter.items.size)
        withNotebook(notebookId) { nb ->
            nb.withChapter(chapterId) { ch -> ch.copy(items = ch.items + item) }
        }
        save()
        item
    }

    /**
     * 添加音频内容（从现有文件路径，不复制）
     */
    suspend fun addAudioContentFromFile(
        notebookId: String,
        chapterId: String,
        relativePath: String
    ): ContentItem? = withContext(Dispatchers.IO) {
        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext null
        val item = ContentItem(type = ContentType.AUDIO, content = relativePath, order = chapter.items.size)
        withNotebook(notebookId) { nb ->
            nb.withChapter(chapterId) { ch -> ch.copy(items = ch.items + item) }
        }
        save()
        item
    }

    /**
     * 添加文件内容（Word/PDF/Excel 等）
     */
    suspend fun addFileContent(notebookId: String, chapterId: String, fileUri: android.net.Uri): ContentItem? = withContext(Dispatchers.IO) {
        val relativePath = fileManager.copyFileToNotebook(context, fileUri, dataDir, notebookId, "FILE")
            ?: return@withContext null

        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext null
        val item = ContentItem(type = ContentType.FILE, content = relativePath, order = chapter.items.size)
        withNotebook(notebookId) { nb ->
            nb.withChapter(chapterId) { ch -> ch.copy(items = ch.items + item) }
        }
        save()
        item
    }

    /**
     * 删除内容条目
     */
    suspend fun deleteContentItem(notebookId: String, chapterId: String, itemId: String) = withContext(Dispatchers.IO) {
        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext
        val item = chapter.items.find { it.id == itemId } ?: return@withContext

        if (item.type != ContentType.TEXT) {
            val contentFile = File(dataDir, item.content)
            val parentDir = contentFile.parentFile ?: return@withContext

            // 1. 从JSON中读取原图路径并删除
            val jsonFile = File(dataDir, "${item.content}.doodles.json")
            if (jsonFile.exists()) {
                try {
                    val json = org.json.JSONObject(jsonFile.readText())
                    val originalPath = json.optString("originalPath", "")
                    if (originalPath.isNotEmpty() && originalPath != item.content) {
                        fileManager.deleteFile(dataDir, originalPath)
                    }
                } catch (_: Exception) {}
            }

            // 2. 如果不是原图，搜索父目录中的原图并删除
            if (contentFile.name.startsWith("edited_")) {
                // 扫描同目录下的所有非edited文件，找到可能的原图
                parentDir.listFiles()?.forEach { file ->
                    if (!file.name.startsWith("edited_")
                        && (file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") || file.name.endsWith(".png"))
                    ) {
                        val relativePath = file.absolutePath.removePrefix("${dataDir.absolutePath}/")
                        if (relativePath != item.content) {
                            fileManager.deleteFile(dataDir, relativePath)
                        }
                    }
                }
            }

            // 3. 删除当前文件及所有伴生文件
            fileManager.deleteFile(dataDir, item.content)
        }

        withNotebook(notebookId) { nb ->
            nb.withChapter(chapterId) { ch ->
                ch.copy(items = ch.items.filterNot { it.id == itemId })
            }
        }
        save()
    }

    /**
     * 更新文字内容
     */
    suspend fun updateTextContent(notebookId: String, chapterId: String, itemId: String, newText: String) = withContext(Dispatchers.IO) {
        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext
        val idx = chapter.items.indexOfFirst { it.id == itemId }
        if (idx >= 0) {
            withNotebook(notebookId) { nb ->
                nb.withChapter(chapterId) { ch ->
                    ch.copy(items = ch.items.map { if (it.id == itemId) it.copy(content = newText) else it })
                }
            }
            save()
        }
    }

    /**
     * 更新图片内容路径（图片编辑后覆盖）
     */
    suspend fun updateImageContentPath(notebookId: String, chapterId: String, itemId: String, newPath: String) = withContext(Dispatchers.IO) {
        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext
        val idx = chapter.items.indexOfFirst { it.id == itemId }
        if (idx >= 0) {
            // 记录旧路径，用于删除旧文件
            val oldPath = chapter.items[idx].content
            withNotebook(notebookId) { nb ->
                nb.withChapter(chapterId) { ch ->
                    ch.copy(items = ch.items.map { if (it.id == itemId) it.copy(content = newPath) else it })
                }
            }
            save()
            // 删除旧的编辑文件和伴生文件（如果路径不同）
            if (oldPath != newPath) {
                val oldFile = File(dataDir, oldPath)
                val isOriginal = !oldFile.name.startsWith("edited_")
                if (!isOriginal) {
                    // 旧的编辑文件可以删除（原图保留不动）
                    fileManager.deleteFile(dataDir, oldPath)
                } else {
                    // 原图只清理伴生文件
                    File(dataDir, "$oldPath.base").let { if (it.exists()) it.delete() }
                    File(dataDir, "$oldPath.strokes").let { if (it.exists()) it.delete() }
                }
            }
        }
    }

    /**
     * 切换内容条目标记
     */
    suspend fun toggleContentMarked(notebookId: String, chapterId: String, itemId: String) = withContext(Dispatchers.IO) {
        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext
        val idx = chapter.items.indexOfFirst { it.id == itemId }
        if (idx >= 0) {
            withNotebook(notebookId) { nb ->
                nb.withChapter(chapterId) { ch ->
                    ch.copy(items = ch.items.map {
                        if (it.id == itemId) it.copy(isMarked = !it.isMarked) else it
                    })
                }
            }
            save()
        }
    }

    /**
     * 切换章节标记
     */
    suspend fun toggleChapterMarked(notebookId: String, chapterId: String) = withContext(Dispatchers.IO) {
        val notebook = data.notebooks.find { it.id == notebookId } ?: return@withContext
        val chapter = findChapter(notebook.chapters, chapterId) ?: return@withContext
        withNotebook(notebookId) { nb ->
            nb.withChapter(chapterId) { ch -> ch.copy(isMarked = !ch.isMarked) }
        }
        save()
    }

    /**
     * 获取文件的绝对路径
     */
    fun getAbsolutePath(relativePath: String): File {
        return File(dataDir, relativePath)
    }

    /**
     * 获取笔记本文件目录
     */
    fun getNotebookDir(notebookId: String): File {
        return File(dataDir, notebookId)
    }

    // ==================== 章节排序 ====================

    /**
     * 交换两个同级章节的顺序
     */
    suspend fun swapChapters(notebookId: String, chapterId1: String, chapterId2: String) = withContext(Dispatchers.IO) {
        val notebook = data.notebooks.find { it.id == notebookId } ?: return@withContext
        val parent1 = findParent(notebook.chapters, chapterId1)
        val parent2 = findParent(notebook.chapters, chapterId2)

        val targetParentId: String? = if (parent1 != null && parent1 == parent2) parent1.id
                                    else if (parent1 == null && parent2 == null) null
                                    else return@withContext  // 不是同级节点

        val siblingList = if (targetParentId != null) {
            findChapter(notebook.chapters, targetParentId)!!.children
        } else {
            notebook.chapters
        }
        val idx1 = siblingList.indexOfFirst { it.id == chapterId1 }
        val idx2 = siblingList.indexOfFirst { it.id == chapterId2 }
        if (idx1 < 0 || idx2 < 0) return@withContext

        val ch1 = siblingList[idx1]
        val ch2 = siblingList[idx2]
        val switched = siblingList.toMutableList().also {
            it[idx1] = ch2.copy(order = ch1.order)
            it[idx2] = ch1.copy(order = ch2.order)
        }.toList()

        if (targetParentId != null) {
            withNotebook(notebookId) { nb ->
                nb.withChapter(targetParentId) { ch -> ch.copy(children = switched) }
            }
        } else {
            withNotebook(notebookId) { nb -> nb.copy(chapters = switched) }
        }
        save()
    }

    // ==================== 内容排序 ====================

    /**
     * 交换两个内容条目的顺序
     */
    suspend fun swapContentItems(notebookId: String, chapterId: String, itemId1: String, itemId2: String) = withContext(Dispatchers.IO) {
        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext
        val idx1 = chapter.items.indexOfFirst { it.id == itemId1 }
        val idx2 = chapter.items.indexOfFirst { it.id == itemId2 }
        if (idx1 < 0 || idx2 < 0) return@withContext

        val item1 = chapter.items[idx1]
        val item2 = chapter.items[idx2]
        val swapped = chapter.items.toMutableList().also {
            it[idx1] = item2.copy(order = item1.order)
            it[idx2] = item1.copy(order = item2.order)
        }.toList()

        withNotebook(notebookId) { nb ->
            nb.withChapter(chapterId) { ch -> ch.copy(items = swapped) }
        }
        save()
    }

    /**
     * 移动内容条目到新位置
     */
    suspend fun moveContentItem(notebookId: String, chapterId: String, itemId: String, newOrder: Int) = withContext(Dispatchers.IO) {
        val chapter = findChapterInNotebook(notebookId, chapterId) ?: return@withContext
        val item = chapter.items.find { it.id == itemId } ?: return@withContext
        val oldIndex = chapter.items.indexOf(item)
        if (oldIndex < 0) return@withContext

        val mutable = chapter.items.toMutableList()
        mutable.removeAt(oldIndex)
        val insertIndex = newOrder.coerceIn(0, mutable.size)
        mutable.add(insertIndex, item.copy(order = newOrder))

        val reindexed = mutable.mapIndexed { idx, ci -> ci.copy(order = idx) }

        withNotebook(notebookId) { nb ->
            nb.withChapter(chapterId) { ch -> ch.copy(items = reindexed) }
        }
        save()
    }

    // ==================== 阅读位置跟踪 ====================

    /**
     * 更新笔记本的上次阅读章节
     */
    suspend fun updateLastReadChapterId(notebookId: String, chapterId: String) = withContext(Dispatchers.IO) {
        withNotebook(notebookId) { nb -> nb.copy(lastReadChapterId = chapterId) }
        save()
    }

    // ==================== 编辑笔记本 ====================

    /**
     * 更新笔记本信息
     */
    suspend fun updateNotebookInfo(notebookId: String, title: String, description: String) = withContext(Dispatchers.IO) {
        withNotebook(notebookId) { nb -> nb.copy(title = title, description = description) }
        save()
    }

    // ==================== 搜索功能 ====================

    /**
     * 搜索结果数据类
     */
    data class SearchResult(
        val notebookId: String,
        val notebookTitle: String,
        val chapterId: String,
        val chapterTitle: String,
        val matchedText: String,
        val matchType: String,
        val ancestorPath: String = ""
    )

    /**
     * 全局搜索
     */
    fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val results = mutableListOf<SearchResult>()
        val lowerQuery = query.lowercase()

        for (notebook in data.notebooks) {
            if (notebook.title.lowercase().contains(lowerQuery)) {
                results.add(
                    SearchResult(
                        notebookId = notebook.id,
                        notebookTitle = notebook.title,
                        chapterId = notebook.chapters.firstOrNull()?.id ?: "",
                        chapterTitle = "",
                        matchedText = notebook.title,
                        matchType = "title"
                    )
                )
            }
            searchChapters(notebook, notebook.chapters, lowerQuery, results, "")
        }

        return results
    }

    private fun searchChapters(
        notebook: Notebook,
        chapters: List<Chapter>,
        lowerQuery: String,
        results: MutableList<SearchResult>,
        ancestorTitles: String
    ) {
        for (chapter in chapters) {
            val path = if (ancestorTitles.isEmpty()) chapter.title
                       else "$ancestorTitles > ${chapter.title}"

            if (chapter.title.lowercase().contains(lowerQuery)) {
                results.add(
                    SearchResult(
                        notebookId = notebook.id,
                        notebookTitle = notebook.title,
                        chapterId = chapter.id,
                        chapterTitle = chapter.title,
                        matchedText = chapter.title,
                        matchType = "title",
                        ancestorPath = ancestorTitles
                    )
                )
            }

            for (item in chapter.items) {
                if (item.type == ContentType.TEXT && item.content.lowercase().contains(lowerQuery)) {
                    results.add(
                        SearchResult(
                            notebookId = notebook.id,
                            notebookTitle = notebook.title,
                            chapterId = chapter.id,
                            chapterTitle = chapter.title,
                            matchedText = item.content,
                            matchType = "content",
                            ancestorPath = ancestorTitles
                        )
                    )
                }
            }

            searchChapters(notebook, chapter.children, lowerQuery, results, path)
        }
    }

    // ==================== 数据目录管理 ====================

    /**
     * 设置新的数据目录（用于迁移数据）
     */
    suspend fun setDataDir(newDir: File) = withContext(Dispatchers.IO) {
        dataDir = newDir
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        data = jsonStorage.loadData(dataDir, dataFileName)
    }
}
