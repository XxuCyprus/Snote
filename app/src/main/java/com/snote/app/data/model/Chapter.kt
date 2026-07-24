package com.snote.app.data.model

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * 章节 - 目录结构中的一个节点
 *
 * 章节通过children字段实现树形目录结构（最多3级）：
 *   level=1: 章，如"第1章 行列式"
 *   level=2: 节，如"1.1 行列式的定义"
 *   level=3: 小节，如"1.1.1 二阶行列式"
 *
 * @property id 唯一标识符
 * @property title 章节标题
 * @property level 层级深度（1=章, 2=节, 3=小节）
 * @property order 在同级目录中的排列顺序
 * @property children 子章节列表（递归结构）
 * @property items 当前章节下的内容条目列表
 */
@Immutable
data class Chapter(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val level: Int = 1,
    val order: Int = 0,
    val children: List<Chapter> = emptyList(),
    val items: List<ContentItem> = emptyList(),
    val isMarked: Boolean = false
)
