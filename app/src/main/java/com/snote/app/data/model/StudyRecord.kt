package com.snote.app.data.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.util.UUID

/**
 * 学习时间记录 - 单次/累计学习时长
 */
@Immutable
data class StudyRecord(
    val id: String = UUID.randomUUID().toString(),
    val notebookId: String,
    val date: String = LocalDate.now().toString(), // yyyy-MM-dd
    val durationSeconds: Long = 0
)
