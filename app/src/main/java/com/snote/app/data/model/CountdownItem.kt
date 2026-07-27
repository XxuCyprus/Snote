package com.snote.app.data.model

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * 倒数日条目
 */
@Immutable
data class CountdownItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetDate: Long, // epoch millis
    val createdAt: Long = System.currentTimeMillis()
)
