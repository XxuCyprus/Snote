# 📖 Snote — 为学习打造的多媒体整理工具

<p align="center">
  <img src="https://img.shields.io/badge/version-4.0.0-blue" alt="version">
  <img src="https://img.shields.io/badge/Android-8.0+-green" alt="minSdk">
  <img src="https://img.shields.io/badge/Compose-Material%203-purple" alt="Compose">
  <img src="https://img.shields.io/badge/Kotlin-2.1.20-red" alt="Kotlin">
</p>

> Snote 是一款专注学习场景的综合整理工具，以笔记管理、待办追踪、学习统计、倒数日四大模块为核心，整合文字、图片、视频、音频与文件等多种内容，提供章节分级管理、图片涂鸦编辑、自动学习计时与数据持久保护等能力，打造结构化的整理与阅读体验。

---

## ✨ 核心特点

### 📚 我的笔记
- **五种内容类型** — 文字 / 图片 / 视频 / 音频 / 文件（Word、PDF、Excel）
- **多级章节** — 笔记本 → 章 → 节 → 小节，最多 6 层嵌套
- **图片涂鸦编辑** — 内置手绘涂鸦、文字标注、裁切旋转，支持撤销与重做
- **应用内录音** — 麦克风录制音频，内置播放条支持进度拖拽
- **文件导入** — 支持 Word、PDF、Excel 导入，通过系统应用打开查看
- **内容重命名** — 视频、音频、文件支持自定义命名
- **星标过滤** — 标记重点内容，一键筛选
- **全文本搜索** — 搜笔记本名、章节名、文字内容，同时支持按视频/音频/文件名搜索

### ✅ 待办中心
- **双区管理** — 未完成 / 已完成分区，支持复选框完成确认与撤销
- **全类型内容** — 与笔记模块一致，支持文字、图片、视频、音频、文件
- **图片涂鸦编辑** — 与笔记模块相同的编辑体验

### 📊 专注统计
- **扇形图** — 当日学习时长可视化展示，Top3 科目 + 其他分组
- **自动计时** — 进入阅读页自动开始计时，切到后台自动暂停
- **日期切换** — 查看任意日期的学习统计

### ⏳ 倒数日
- **两列网格** — 倒计时卡片一目了然
- **日期选择** — 点击 FAB 添加，日历选择目标日期
- **编辑管理** — 点击卡片可修改名称 / 日期，内设删除按钮

---

### 🎨 界面与体验
- **首页仪表盘** — 四模块卡片入口，渐变色装饰条 + 压感动画
- **阅读记忆** — 再次打开笔记本自动跳转到上次阅读位置
- **侧滑目录** — 左侧抽屉式章节大纲，快速切换
- **物理动画** — 页面切换与弹窗采用 Spring 弹性动画，过渡流畅自然

---

## 📱 存储与隐私

所有数据存储在 `Documents/Snote/` 目录下，App 不携带任何用户内容：

- **极轻量** — App 体积不受内容数量影响
- **卸载不丢失** — 删除 App 后数据文件保留，重新安装自动恢复
- **隐私安全** — 无网络权限，所有数据全程保存于本地
- **数据保护** — 强制权限校验，JSON 原子写入防崩溃损坏，权限变化时自动切换存储路径

> 系统会请求「所有文件访问」权限用于读写 `Documents/Snote/` 目录。v4.0.0 起，不授予权限将无法使用 App。

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
│           ├── screen/main/      # 主页仪表盘（四模块入口）
│           ├── screen/home/      # 我的笔记（笔记本列表）
│           ├── screen/todo/      # 待办中心
│           ├── screen/stats/     # 专注统计
│           ├── screen/countdown/ # 倒数日
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
