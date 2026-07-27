package com.snote.app.data.repository

import android.content.Context
import android.os.Environment
import com.snote.app.data.model.ContentItem
import com.snote.app.data.model.ContentType
import com.snote.app.data.model.TodoBoard
import com.snote.app.data.storage.FileManager
import com.snote.app.data.storage.JsonStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonStorage: JsonStorage,
    private val fileManager: FileManager
) {
    private val gson = Gson()
    private var board = TodoBoard()

    init {
        kotlinx.coroutines.runBlocking {
            loadTodos()
        }
    }

    private fun getDataDir(): File {
        return try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            File(documentsDir, "Snote")
        } catch (_: Exception) {
            File(context.filesDir, "Snote")
        }
    }

    private suspend fun ensureDataDir() = withContext(Dispatchers.IO) {
        val dir = getDataDir()
        if (!dir.exists()) dir.mkdirs()
    }

    fun getDataDirPath(): String = getDataDir().absolutePath

    suspend fun loadTodos() = withContext(Dispatchers.IO) {
        ensureDataDir()
        val file = File(getDataDir(), "snote_todos.json")
        if (file.exists()) {
            try {
                val text = file.readText()
                val type = object : TypeToken<TodoBoard>() {}.type
                board = gson.fromJson(text, type) ?: TodoBoard()
            } catch (_: Exception) {
                board = TodoBoard()
            }
        } else {
            board = TodoBoard()
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        ensureDataDir()
        val file = File(getDataDir(), "snote_todos.json")
        file.writeText(gson.toJson(board))
    }

    fun getBoard(): TodoBoard = board

    suspend fun addTodoItem(section: String, item: ContentItem) = withContext(Dispatchers.IO) {
        board = when (section) {
            "unfinished" -> board.copy(unfinished = board.unfinished + item)
            else -> board
        }
        save()
    }

    suspend fun addImageContent(section: String, uri: android.net.Uri): ContentItem? = withContext(Dispatchers.IO) {
        val dataDir = getDataDir()
        val relativePath = fileManager.copyFileToNotebook(context, uri, dataDir, "todos", "IMAGE") ?: return@withContext null
        val item = ContentItem(type = ContentType.IMAGE, content = relativePath, order = board.unfinished.size)
        board = board.copy(unfinished = board.unfinished + item)
        save()
        item
    }

    suspend fun addVideoContent(section: String, uri: android.net.Uri): ContentItem? = withContext(Dispatchers.IO) {
        val dataDir = getDataDir()
        val relativePath = fileManager.copyFileToNotebook(context, uri, dataDir, "todos", "VIDEO") ?: return@withContext null
        val item = ContentItem(type = ContentType.VIDEO, content = relativePath, order = board.unfinished.size)
        board = board.copy(unfinished = board.unfinished + item)
        save()
        item
    }

    suspend fun addAudioContent(section: String, uri: android.net.Uri): ContentItem? = withContext(Dispatchers.IO) {
        val dataDir = getDataDir()
        val relativePath = fileManager.copyFileToNotebook(context, uri, dataDir, "todos", "AUDIO") ?: return@withContext null
        val item = ContentItem(type = ContentType.AUDIO, content = relativePath, order = board.unfinished.size)
        board = board.copy(unfinished = board.unfinished + item)
        save()
        item
    }

    suspend fun addFileContent(section: String, uri: android.net.Uri): ContentItem? = withContext(Dispatchers.IO) {
        val dataDir = getDataDir()
        val relativePath = fileManager.copyFileToNotebook(context, uri, dataDir, "todos", "FILE") ?: return@withContext null
        val item = ContentItem(type = ContentType.FILE, content = relativePath, order = board.unfinished.size)
        board = board.copy(unfinished = board.unfinished + item)
        save()
        item
    }

    suspend fun addTextContent(section: String, text: String): ContentItem? = withContext(Dispatchers.IO) {
        val item = ContentItem(type = ContentType.TEXT, content = text, order = board.unfinished.size)
        board = board.copy(unfinished = board.unfinished + item)
        save()
        item
    }

    suspend fun markItemDone(itemId: String) = withContext(Dispatchers.IO) {
        val item = board.unfinished.find { it.id == itemId } ?: return@withContext
        board = board.copy(
            unfinished = board.unfinished.filterNot { it.id == itemId },
            finished = board.finished + item.copy(order = board.finished.size)
        )
        save()
    }

    suspend fun undoComplete(itemId: String) = withContext(Dispatchers.IO) {
        val item = board.finished.find { it.id == itemId } ?: return@withContext
        board = board.copy(
            finished = board.finished.filterNot { it.id == itemId },
            unfinished = board.unfinished + item.copy(order = board.unfinished.size)
        )
        save()
    }

    suspend fun deleteTodoItem(itemId: String) = withContext(Dispatchers.IO) {
        val item = board.unfinished.find { it.id == itemId }
            ?: board.finished.find { it.id == itemId }
            ?: return@withContext

        if (item.type != ContentType.TEXT) {
            val dataDir = getDataDir()
            val contentFile = File(dataDir, item.content)
            if (contentFile.exists()) contentFile.delete()
        }

        board = board.copy(
            unfinished = board.unfinished.filterNot { it.id == itemId },
            finished = board.finished.filterNot { it.id == itemId }
        )
        save()
    }

    suspend fun swapTodoItems(itemId1: String, itemId2: String) = withContext(Dispatchers.IO) {
        // Try to find items in unfinished first, then finished
        val idx1_u = board.unfinished.indexOfFirst { it.id == itemId1 }
        val idx2_u = board.unfinished.indexOfFirst { it.id == itemId2 }
        if (idx1_u >= 0 && idx2_u >= 0) {
            val item1 = board.unfinished[idx1_u]
            val item2 = board.unfinished[idx2_u]
            val swapped = board.unfinished.toMutableList().also {
                it[idx1_u] = item2.copy(order = item1.order)
                it[idx2_u] = item1.copy(order = item2.order)
            }.toList()
            board = board.copy(unfinished = swapped)
            save()
        }
    }

    suspend fun renameTodoItem(itemId: String, newName: String) = withContext(Dispatchers.IO) {
        val item = board.unfinished.find { it.id == itemId }
            ?: board.finished.find { it.id == itemId }
            ?: return@withContext

        if (item.type != ContentType.VIDEO && item.type != ContentType.AUDIO && item.type != ContentType.FILE)
            return@withContext

        val dataDir = getDataDir()
        val oldFile = File(dataDir, item.content)
        if (!oldFile.exists()) return@withContext

        val parentDir = oldFile.parentFile ?: return@withContext
        val extension = oldFile.extension
        val extWithDot = if (extension.isNotEmpty()) ".$extension" else ""
        val sanitized = newName.replace(Regex("""[/\\:*?"<>|]"""), "_").trim()
        if (sanitized.isEmpty()) return@withContext

        var newFile = File(parentDir, "$sanitized$extWithDot")
        if (newFile.exists() && newFile.absolutePath != oldFile.absolutePath) {
            var counter = 1
            while (true) {
                newFile = File(parentDir, "$sanitized($counter)$extWithDot")
                if (!newFile.exists()) break
                counter++
            }
        }

        oldFile.renameTo(newFile)
        val newRelativePath = newFile.absolutePath.removePrefix("${dataDir.absolutePath}/")
        val updated = item.copy(content = newRelativePath)

        board = board.copy(
            unfinished = board.unfinished.map { if (it.id == itemId) updated else it },
            finished = board.finished.map { if (it.id == itemId) updated else it }
        )
        save()
    }
}
