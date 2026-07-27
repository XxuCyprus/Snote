package com.snote.app.data.repository

import com.snote.app.data.model.CountdownItem
import com.snote.app.data.storage.AppDataManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CountdownRepository @Inject constructor(
    private val appDataManager: AppDataManager
) {
    private val gson = Gson()
    private var items: List<CountdownItem> = emptyList()

    private val dataDir: File get() = appDataManager.dataDir

    init {
        kotlinx.coroutines.runBlocking {
            loadCountdowns()
        }
    }

    private suspend fun ensureDataDir() = withContext(Dispatchers.IO) {
        val dir = dataDir
        if (!dir.exists()) dir.mkdirs()
    }

    suspend fun loadCountdowns() = withContext(Dispatchers.IO) {
        ensureDataDir()
        val file = File(dataDir, "snote_countdowns.json")
        if (file.exists()) {
            try {
                val text = file.readText()
                val type = object : TypeToken<List<CountdownItem>>() {}.type
                items = gson.fromJson(text, type) ?: emptyList()
            } catch (_: Exception) {
                items = emptyList()
            }
        } else {
            items = emptyList()
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        try {
            ensureDataDir()
            val file = File(dataDir, "snote_countdowns.json")
            file.writeText(gson.toJson(items))
        } catch (e: Exception) {
            android.util.Log.e("CountdownRepo", "保存失败: ${e.message}", e)
            // 保存失败时不修改内存数据
        }
    }

    suspend fun addCountdown(title: String, targetDate: Long) = withContext(Dispatchers.IO) {
        val item = CountdownItem(title = title, targetDate = targetDate)
        items = items + item
        save()
        item
    }

    suspend fun deleteCountdown(id: String) = withContext(Dispatchers.IO) {
        items = items.filterNot { it.id == id }
        save()
    }

    suspend fun updateCountdown(id: String, newTitle: String, newTargetDate: Long) = withContext(Dispatchers.IO) {
        items = items.map { if (it.id == id) it.copy(title = newTitle, targetDate = newTargetDate) else it }
        save()
    }

    fun getCountdowns(): List<CountdownItem> = items

    data class CountdownWithDays(
        val item: CountdownItem,
        val daysRemaining: Long
    )

    fun getCountdownsWithDays(): List<CountdownWithDays> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        return items.sortedBy { it.targetDate }.map { item ->
            val days = kotlin.math.ceil((item.targetDate - now).toDouble() / dayMs).toLong()
            CountdownWithDays(item, days)
        }
    }
}
