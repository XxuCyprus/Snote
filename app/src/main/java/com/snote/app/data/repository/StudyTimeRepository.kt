package com.snote.app.data.repository

import android.content.Context
import android.os.Environment
import com.snote.app.data.model.StudyRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudyTimeRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private var records: MutableList<StudyRecord> = mutableListOf()

    init {
        kotlinx.coroutines.runBlocking {
            loadRecords()
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

    suspend fun loadRecords() = withContext(Dispatchers.IO) {
        ensureDataDir()
        val file = File(getDataDir(), "snote_study_time.json")
        if (file.exists()) {
            try {
                val text = file.readText()
                val type = object : TypeToken<MutableList<StudyRecord>>() {}.type
                records = gson.fromJson(text, type) ?: mutableListOf()
            } catch (_: Exception) {
                records = mutableListOf()
            }
        } else {
            records = mutableListOf()
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        ensureDataDir()
        val file = File(getDataDir(), "snote_study_time.json")
        file.writeText(gson.toJson(records))
    }

    suspend fun addDuration(notebookId: String, date: String, seconds: Long) = withContext(Dispatchers.IO) {
        val existing = records.find { it.notebookId == notebookId && it.date == date }
        if (existing != null) {
            val idx = records.indexOf(existing)
            records[idx] = existing.copy(durationSeconds = existing.durationSeconds + seconds)
        } else {
            records.add(StudyRecord(notebookId = notebookId, date = date, durationSeconds = seconds))
        }
        save()
    }

    fun getTodayRecords(): List<StudyRecord> {
        val today = java.time.LocalDate.now().toString()
        return records.filter { it.date == today }
    }

    fun getAllRecords(): List<StudyRecord> = records.toList()
}
