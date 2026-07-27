# 📖 Snote — 为学习打造的多媒体整理工具

<p align="center">
  <img src="https://img.shields.io/badge/version-4.0.0-blue" alt="version">
  <img src="https://img.shields.io/badge/Android-8.0+-green" alt="minSdk">
  <img src="https://img.shields.io/badge/Compose-Material%203-purple" alt="Compose">
  <img src="https://img.shields.io/badge/Kotlin-2.1.20-red" alt="Kotlin">
</p>

> 一款极轻量的学习笔记应用。将图片、视频、音频、文件按笔记本与章节层级规整收纳，配合涂鸦编辑、内容重命名、物理动画，打造结构化的电子书式阅读体验。

---

## 🆕 v4.0.0 更新内容

### 🏠 首页模块化
- **全新仪表盘** — 四模块入口（我的笔记、待办中心、专注统计、倒数日），2×2 卡片布局
- 每张卡片带渐变色装饰条和压感动画

### ✅ 待办中心
- 未完成 / 已完成双区管理
- 支持文字、图片、视频、音频、文件五种内容类型
- 复选框完成确认，可撤销
- 图片涂鸦编辑（与笔记模块一致）

### 📊 专注统计
- Canvas 扇形图展示当日学习时长
- 自动学习计时（进入阅读即开始，后台自动暂停）
- 日期切换查看历史统计
- Top3 + 其他科目分组

### ⏳ 倒数日
- 两列网格布局
- 日期选择器添加
- 点击编辑名称/日期，内设删除按钮

### 🔒 数据保护全面加固
- 权限门控：启动强制检查 + 每次 `onResume` 二次验证
- 智能存储切换：权限变化时自动切换内部/外部分区
- JSON 原子写入：防止崩溃导致文件损坏
- 紧急备份：权限撤销时自动保存到内部存储
- 图片编辑后自动清理旧版本，启动时清理孤儿文件

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
- **全文本搜索** — 搜笔记本名、章节名、文字内容，同时支持按视频/音频/文件名搜索

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

> 系统会请求「所有文件访问」权限用于读写 `Documents/Snote/` 目录。v4.0.0 起，不授予权限将无法使用 App，以确保数据安全。

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
│           ├── screen/main/      # 主页仪表盘（v4.0.0 新增）
│           ├── screen/home/      # 我的笔记（笔记本列表）
│           ├── screen/todo/      # 待办中心（v4.0.0 新增）
│           ├── screen/stats/     # 专注统计（v4.0.0 新增）
│           ├── screen/countdown/ # 倒数日（v4.0.0 新增）
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

## 📄 许可证

本项目采用 [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/) 许可证。使用本项目的代码须遵守署名、非商业性使用、相同方式共享的条款。

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/XxuCyprus">Lnaaa</a>
</p>
