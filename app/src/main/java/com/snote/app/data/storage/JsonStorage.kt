package com.snote.app.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.snote.app.data.model.SnoteData
import java.io.File
import java.io.InputStreamReader

class JsonStorage {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dataFileName = "snote_data.json"
    private val TAG = "JsonStorage"

    fun loadData(dataDir: File, fileName: String = dataFileName): SnoteData {
        val dataFile = File(dataDir, fileName)
        Log.d(TAG, "加载数据: ${dataFile.absolutePath}, 存在: ${dataFile.exists()}, 大小: ${dataFile.length()}")
        if (!dataFile.exists()) {
            Log.d(TAG, "数据文件($fileName)不存在，返回空数据")
            return SnoteData()
        }
        return try {
            InputStreamReader(dataFile.inputStream(), Charsets.UTF_8).use { reader ->
                val result = gson.fromJson(reader, SnoteData::class.java) ?: SnoteData()
                Log.d(TAG, "加载成功($fileName), 笔记本数: ${result.notebooks.size}")
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON解析失败($fileName): ${e.message}", e)
            SnoteData()
        }
    }

    fun saveData(dataDir: File, data: SnoteData, fileName: String = dataFileName): Boolean {
        val dataFile = File(dataDir, fileName)
        return try {
            val json = gson.toJson(data)
            dataFile.writeText(json, Charsets.UTF_8)
            Log.d(TAG, "保存成功: ${dataFile.absolutePath}, 大小: ${dataFile.length()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存失败: ${e.message}", e)
            false
        }
    }

    fun loadDataFromUri(context: Context, uri: Uri): SnoteData {
        return try {
            val file = File(uri.path ?: return SnoteData())
            loadData(file)
        } catch (e: Exception) {
            Log.e(TAG, "从URI加载失败", e)
            SnoteData()
        }
    }
}
