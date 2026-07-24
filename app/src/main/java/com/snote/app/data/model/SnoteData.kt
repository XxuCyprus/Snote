package com.snote.app.data.model

import androidx.compose.runtime.Immutable

/**
 * Snote数据根对象 - 对应snote_data.json文件
 *
 * 这是整个应用数据的顶层结构，序列化后存储在snote_data.json文件中。
 * 所有笔记本、章节、内容条目的数据都存储在这个对象中。
 *
 * @property version 数据格式版本号（用于未来的数据迁移）
 * @property notebooks 笔记本列表
 */
@Immutable
data class SnoteData(
    val version: Int = 1,
    val notebooks: List<Notebook> = emptyList()
)
