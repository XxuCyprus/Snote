package com.snote.app.data.repository

import android.content.Context
import android.os.Environment
import com.snote.app.data.model.CountdownItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CountdownRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private var items: List<CountdownItem> = emptyList()

    init {
        kotlinx.coroutines.runBlocking {
            loadCountdowns()
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

    suspend fun loadCountdowns() = withContext(Dispatchers.IO) {
        ensureDataDir()
        val file = File(getDataDir(), "snote_countdowns.json")
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
        ensureDataDir()
        val file = File(getDataDir(), "snote_countdowns.json")
        file.writeText(gson.toJson(items))
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
