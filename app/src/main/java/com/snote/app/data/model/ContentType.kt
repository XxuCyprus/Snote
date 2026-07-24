package com.snote.app.data.model

/**
 * 内容条目类型枚举
 * 定义了Snote中支持的所有内容类型
 */
enum class ContentType {
    TEXT,    // 文字内容
    IMAGE,   // 图片
    VIDEO,   // 视频（仅显示占位卡片，点击调用系统播放器）
    AUDIO,   // 音频
    FILE     // 其他文件
}
