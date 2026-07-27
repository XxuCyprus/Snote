package com.snote.app.data.model

import androidx.compose.runtime.Immutable

/**
 * 待办看板 - 包含未完成和已完成两个区域
 */
@Immutable
data class TodoBoard(
    val unfinished: List<ContentItem> = emptyList(),
    val finished: List<ContentItem> = emptyList()
)
