# 📖 Snote — 为学习打造的多媒体整理工具

<p align="center">
  <img src="https://img.shields.io/badge/version-3.2.1-blue" alt="version">
  <img src="https://img.shields.io/badge/Android-8.0+-green" alt="minSdk">
  <img src="https://img.shields.io/badge/Compose-Material%203-purple" alt="Compose">
  <img src="https://img.shields.io/badge/Kotlin-2.1.20-red" alt="Kotlin">
</p>

> 一款极轻量的学习笔记应用。将图片、视频、音频、文件按笔记本与章节层级规整收纳，配合涂鸦编辑、内容重命名、物理动画，打造结构化的电子书式阅读体验。

---

## ✨ 核心特点

### 📚 内容管理
- **五种内容类型** — 文字 / 图片 / 视频 / 音频 / 文件（Word、PDF、Excel）
- **多级章节** — 笔记本 → 章 → 节 → 小节，最多支持 6 层嵌套
- **多媒体编辑** — 内置涂鸦手绘、文字标注、裁切旋转，支持撤销重做
- **应用内录音** — 麦克风录制音频，内置播放条支持进度拖拽
- **文件导入** — 支持 Word、PDF、Excel 文件的导入，通过系统应用打开查看
- **内容重命名** — 视频、音频、文件支持自定义命名，方便长期管理
- **星标过滤** — 标记重点内容，一键筛选目标条目
- **全文本搜索** — 搜索范围覆盖笔记本名、章节名、文字内容，点击直达

### 🎯 阅读体验
- **阅读位置记忆** — 再次打开笔记本自动跳转到上次阅读位置
- **侧滑目录** — 左侧抽屉式章节大纲，快速定位
- **物理动画** — 页面切换与弹窗采用 Spring 弹性动画，过渡流畅自然
- **数据持久与恢复** — 数据存储于公共目录，卸载不丢失；即使索引文件损坏，也可通过文件系统扫描重建

---

## 📱 存储与隐私

所有数据存储在手机的 `Documents/Snote/` 目录下，App 本体仅包含代码，不携带任何用户内容。这意味着：

- **极轻量** — App 体积不受添加内容数量的影响
- **卸载不丢失** — 删除 App 后媒体文件仍然保留，重新安装即可自动恢复
- **隐私安全** — 无需网络权限，所有数据全程保存在本地

> 系统会请求「所有文件访问」权限用于读写 `Documents/Snote/` 目录；不授予权限仍可正常使用，但卸载后数据将无法恢复。麦克风权限仅在录音时使用。

---

## 🛠 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository |
| 依赖注入 | Hilt（Dagger） |
| 图片加载 | Coil |
| JSON 解析 | Gson |
| 数据存储 | DataStore Preferences |

---

## 🚀 使用方式

### 直接下载安装（推荐）
前往 [Releases 页面](https://github.com/XxuCyprus/Snote/releases) 下载最新 APK。

### 从源码构建
```bash
git clone https://github.com/XxuCyprus/Snote.git
```
用 Android Studio 打开项目，等待 Gradle Sync 完成后 Run。

---

## 📂 项目结构

```
Snote/
├── app/                          # 主应用模块
│   └── src/main/java/com/snote/app/
│       ├── data/                 # 数据模型 & 仓库 & 文件管理
│       ├── di/                   # Hilt 依赖注入
│       └── ui/                   # Compose UI 层
│           ├── screen/home/      # 首页（笔记本列表）
│           ├── screen/reader/    # 阅读页（章节 + 内容）
│           ├── screen/settings/  # 设置页
│           ├── screen/search/    # 搜索页
│           ├── navigation/       # 导航图
│           └── theme/            # Material 3 主题
├── image/                        # 图片编辑器独立模块
│   └── src/main/java/me/minetsh/imaging/
│       ├── core/                 # 核心编辑逻辑（涂鸦/贴纸/裁切/动画）
│       └── view/                 # 自定义 View 组件
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/XxuCyprus">Lnaaa</a>
</p>
