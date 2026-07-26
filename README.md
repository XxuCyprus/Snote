# 📖 Snote — 为学习打造的多媒体整理工具

<p align="center">
  <img src="https://img.shields.io/badge/version-3.0.2-blue" alt="version">
  <img src="https://img.shields.io/badge/Android-8.0+-green" alt="minSdk">
  <img src="https://img.shields.io/badge/Compose-Material%203-purple" alt="Compose">
  <img src="https://img.shields.io/badge/Kotlin-2.1.20-red" alt="Kotlin">
</p>

> 一本专注于**整理**的学习助手。将手机中零散的照片、视频，按笔记本与章节层级规整收纳；在需要时补充文字说明或语音录音。配合流畅的物理动画过渡，让知识浏览成为一种享受。

---

## ✨ 核心特点

### 📚 以整理为主，文字录音为辅
- 图片与视频**从手机相册导入**——帮助你将杂乱图库梳理成清晰的知识体系
- 文字与录音**由 App 生成**——查看内容后有想法，随时撰写或录制
- 支持笔记本 → 章节 → 小节的多级分类，最多 **6 层嵌套**

### 🎬 流畅的物理动画
页面切换采用物理弹簧（Spring）动画模型，模拟真实世界的弹性效果，过渡自然、不突兀。弹窗从触发按钮位置缩放展开，细节考究。

### 🎨 内置图片编辑
- **涂鸦手绘** — 自由画笔 + 6 色调色盘 + 可调粗细
- **文字标注** — 在图片上叠加彩色文字
- **裁切旋转** — 裁剪画面或旋转角度
- **撤销重做** — 完整编辑历史，随时回退

### 🎵 四种内容类型
| 类型 | 来源 | 交互 |
|------|------|------|
| 🖼️ 图片 | 手机相册导入 | 全屏查看 + 捏合缩放 + 编辑标注 |
| 🎬 视频 | 手机相册导入 | 系统播放器播放 |
| 📝 文字 | App 内生成 | 点击编辑 |
| 🎙️ 录音 | App 内生成 | 内置播放条 + 录制 |

### 🔍 全文本搜索
搜索范围覆盖笔记本名、章节名、文字内容，结果附带层级路径，点击直达目标章节。

### 🎯 阅读体验
- **阅读位置记忆** — 再次打开笔记本自动跳转到上次阅读位置
- **侧滑目录** — 左侧抽屉式章节大纲，快速定位
- **星标系统** — 标记重要内容，支持"仅显示已标记"过滤
- **数据持久化** — 卸载 App 后数据不丢失，重新安装即可自动恢复

---

## 📱 系统要求

| 项目 | 要求 |
|------|------|
| Android 版本 | 8.0 及以上 |
| 安装包大小 | 约 2 MB（极轻量） |

**权限说明：**

| 权限 | 用途 |
|------|------|
| 所有文件访问 | 将数据存储在公共目录，实现卸载重装后自动恢复 |
| 麦克风 | 应用内录音 |

> 即使不授予文件访问权限，导入图片视频等基础功能仍可正常使用，但卸载后数据将无法恢复。
> 
> 本 App **不使用网络权限**，所有数据仅存储在本地。

---

## 🚀 使用方式

### 方式一：直接下载安装（推荐）
前往 [Releases 页面](https://github.com/XxuCyprus/Snote/releases) 下载最新 APK 安装即可。

### 方式二：从源码构建
```bash
git clone https://github.com/XxuCyprus/Snote.git
```
用 Android Studio 打开项目，等待 Gradle Sync 完成后点击 Run。

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
│           ├── screen/reader/    # 阅读页（章节+内容）
│           ├── screen/settings/  # 设置页
│           ├── screen/search/    # 搜索页
│           ├── navigation/       # 导航图
│           └── theme/            # Material 3 主题
├── image/                        # 图片编辑器独立模块
│   └── src/main/java/me/minetsh/imaging/
│       ├── core/                 # 核心编辑逻辑（涂鸦/贴纸/裁切/动画）
│       └── view/                 # 自定义 View 组件
├── build.gradle.kts              # 根构建脚本
├── gradle.properties             # Gradle 配置
└── settings.gradle.kts           # 模块管理
```

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/XxuCyprus">XxuCyprus</a>
</p>
