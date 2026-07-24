package com.snote.app.data.model

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * 内容条目 - 笔记中的最小内容单元
 *
 * 每个章节下可以包含多个内容条目，条目可以是文字、图片、视频等。
 * 条目的content字段含义取决于type：
 *   - TEXT:  直接存储文字内容
 *   - IMAGE: 存储相对路径，如 "notebook_1/images/img_001.jpg"
 *   - VIDEO: 存储相对路径，如 "notebook_1/videos/video_001.mp4"
 *   - AUDIO: 存储相对路径，如 "notebook_1/audio/audio_001.mp3"
 *   - FILE:  存储相对路径，如 "notebook_1/files/doc_001.pdf"
 *
 * @property id 唯一标识符（UUID格式）
 * @property type 内容类型
 * @property content 内容数据（文字内容或文件相对路径）
 * @property order 在当前章节中的排列顺序
 */
@Immutable
data class ContentItem(
    val id: String = UUID.randomUUID().toString(),
    val type: ContentType,
    val content: String,
    val order: Int = 0,
    val isMarked: Boolean = false
)
