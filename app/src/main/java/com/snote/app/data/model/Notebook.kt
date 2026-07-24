package com.snote.app.data.model

import androidx.compose.runtime.Immutable
import java.time.LocalDateTime
import java.util.UUID

/**
 * 笔记本 - 顶层数据容器
 *
 * 一个笔记本代表一门课程或一个主题，如"线性代数"、"大学物理"。
 * 笔记本下包含多个章节（Chapter），章节可以嵌套形成目录树。
 *
 * @property id 唯一标识符
 * @property title 笔记本名称
 * @property description 描述信息（可选）
 * @property cover 封面图片的相对路径（可选）
 * @property createdAt 创建时间
 * @property chapters 章节列表（目录树）
 */
@Immutable
data class Notebook(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val cover: String? = null,
    val createdAt: String = LocalDateTime.now().toString(),
    val chapters: List<Chapter> = emptyList(),
    val lastReadChapterId: String? = null
)
