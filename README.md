# 课程表 App（Android）

<p align="center">
  <a href="https://github.com/liangleionline/CourseScheduleApp/releases/latest"><img src="https://img.shields.io/github/v/release/liangleionline/CourseScheduleApp?label=%E6%9C%80%E6%96%B0%E7%89%88%E6%9C%AC&color=2ea44f" alt="最新版本"></a>
  <a href="https://github.com/liangleionline/CourseScheduleApp/releases"><img src="https://img.shields.io/github/downloads/liangleionline/CourseScheduleApp/total?label=%E6%80%BB%E4%B8%8B%E8%BD%BD&color=007ec6" alt="总下载"></a>
  <a href="https://github.com/liangleionline/CourseScheduleApp/releases/latest"><img src="https://img.shields.io/github/downloads/liangleionline/CourseScheduleApp/latest/total?label=%E6%9C%80%E6%96%B0%E7%89%88%E4%B8%8B%E8%BD%BD&color=007ec6" alt="最新版下载"></a>
  <a href="https://github.com/liangleionline/CourseScheduleApp/releases"><img src="https://img.shields.io/github/downloads/liangleionline/CourseScheduleApp/app-release.apk/total?label=APK%E4%B8%8B%E8%BD%BD&color=007ec6" alt="APK下载"></a>
  <a href="https://github.com/liangleionline/CourseScheduleApp#license"><img src="https://img.shields.io/github/license/liangleionline/CourseScheduleApp?label=%E8%AE%B8%E5%8F%AF%E8%AF%81&color=orange" alt="许可证"></a>
  <a href="https://github.com/liangleionline/CourseScheduleApp"><img src="https://img.shields.io/github/stars/liangleionline/CourseScheduleApp?label=Stars&color=yellow" alt="Stars"></a>
  <a href="https://github.com/liangleionline/CourseScheduleApp"><img src="https://img.shields.io/github/forks/liangleionline/CourseScheduleApp?label=Forks&color=blue" alt="Forks"></a>
  <a href="https://github.com/liangleionline/CourseScheduleApp/commits/main"><img src="https://img.shields.io/github/last-commit/liangleionline/CourseScheduleApp?label=%E6%9C%80%E8%BF%91%E6%8F%90%E4%BA%A4" alt="最近提交"></a>
  <a href="https://github.com/liangleionline/CourseScheduleApp/graphs/commit-activity"><img src="https://img.shields.io/github/commit-activity/m/liangleionline/CourseScheduleApp?label=%E6%9C%88%E6%8F%90%E4%BA%A4" alt="月提交"></a>
  <a href="https://github.com/liangleionline/CourseScheduleApp"><img src="https://visitor-badge.laobi.icu/badge?page_id=liangleionline.CourseScheduleApp&left_text=README%E8%AE%BF%E9%97%AE%E9%87%8F" alt="README访问量"></a>
</p>

一个面向学生的周度课程表安卓应用：首次启动引导完成 4 步配置，首页展示周一至周五课程表，支持单格编辑、课程/非课程自动配色、贯穿全周的非课程项、设置页全局管理、清空课表与周六周日显示开关。

## 功能
- 首次无课表时自动进入 4 步引导：课程库 → 课时时长 → 非课程项库 → 排布周一到周五课表
- 内置预置课程库与非课程项库（可在设置中增删改）
- 全局统一课时时长；非课程项各自独立时长并「贯穿全周」，周一配置一次即可
- 周一选择首节开始时间，周二至周五自动复用、时间自动接续
- 课程自动分配差异化且高对比的配色；同课程同色、不同课程不同色
- 点击任意格编辑，仅限同类型替换（课程↔课程、非课程↔非课程）
- 设置页：课时设置、课程项管理、非课程项管理、周六日显示开关、清空课表
- 本地持久化，无需服务器

## 技术栈
- 原生 Android，Java 17
- 编译 SDK 34，minSdk 24
- 依赖：AndroidX AppCompat / Material / ConstraintLayout
- 存储：SharedPreferences + JSON（org.json）

## 构建
```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk17
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构
```
app/src/main/java/com/courseschedule/app/
  data/       # 数据模型、存储、时间推算引擎、配色
  ui/         # 首页、引导流程、设置页、课表视图
```

## 开源声明

- 本应用中的**桌面小组件**实现复刻自开源项目 [ShiGuangSchedule/shiguangschedule](https://github.com/ShiGuangSchedule/shiguangschedule)（Apache License 2.0，Copyright (C) 2025 XingHeYuZhuan）。
- 详细引用范围与修改说明见 [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)，许可证全文见 [LICENSES/APACHE-2.0.txt](./LICENSES/APACHE-2.0.txt)。

## 数据统计

README 顶部的徽章会实时展示本项目的访问与下载情况：

- **最新版本 / 总下载 / 最新版下载 / APK 下载**：来自 GitHub Releases 附件的下载计数，由 shields.io 实时读取
- **Stars / Forks / 最近提交 / 月提交**：仓库社交数据与提交活动
- **README 访问量**：仓库主页被打开的次数（第三方计数器，每次访问 +1）

> 更详细的流量数据（浏览量、独立访客、克隆数、各 Release 下载明细）见仓库 **Insights → Traffic / Releases**，其中 Traffic 仅仓库所有者可见、仅保留最近 14 天。
