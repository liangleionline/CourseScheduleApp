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

一款面向学生与家长的**周计划课程表安卓应用**：支持多个课程表（每个孩子一套独立设置）、Windows Metro 磁贴风格交互、桌面小组件，完全离线运行。

## 功能

### 课程表与多课表
- 首次启动 4 步引导：课程库 → 课时时长 → 非课程项库 → 排布周一至周五
- **多课程表**：可新增、重命名、删除（至少保留一个）；首页左右滑动切换，顶部标题显示当前课程表名称；旧数据自动迁移
- **每个课程表独立设置**：课程库、非课程项库、课时时长、首节开始时间各自一套，小学 / 初中 / 高中课程时长不同也能分别配置
- 设置页顶部「课表名 + 设置 ▾」下拉菜单，点选直接切换到要设置的那个课程表
- 周视图展示周一至周五（周六日可开关），格子高度随屏幕自适应，一屏放下全部课程
- 课程自动分配差异化高对比配色：同课程同色、不同课程不同色
- 非课程项（午休、眼保健操等）贯穿全周，周一配置一次即可；课时与时间自动接续
- 下拉课表展开被收纳的非课程项，松手自动收起
- 数据管理：删除本课表全部数据 / 仅删除当前课表布局 / 仅删除全部课表布局（后两者保留课程与课时设置）；可导出为单个文件，换机时导入恢复全部数据

### Metro 磁贴交互与动效
- Windows Metro 风格磁贴：课程色块上覆盖一层均匀的半透明玻璃，单块玻璃压平的平面质感
- 入场波浪：打开 App、回到前台、左右切换课程表时，磁贴从右下向左上呈对角波浪浮现
- 点击磁贴 3D 翻转到背面显示详情（课程名 / 老师 / 上课时间分行），再点翻回；点击其他磁贴时上一块自动翻回
- 长按磁贴 500ms 弹出编辑框修改课程
- 双击任一课程：整个课表中该课程的所有磁贴同时上浮漂浮 3 秒（随机方向微摆动）后自动落回
- 当前正在上的课程：入场动画结束后发出白色圆角方形光晕，S 形渐隐渐现约 2 秒
- 左右切换课程表采用 AndroidX ViewPager2 原生跟手滑动（惯性、回弹、边界）

### 桌面小组件
- 提供 4×3 与 4×4 两种尺寸；添加时先选择要显示的课程表
- 顶部显示课程表名称与日期，列表显示当天 / 次日课程
- WorkManager 定时刷新，跨天自动更新

### 数据与隐私
- 完全离线：数据保存在本地（SharedPreferences + JSON），不联网、无账号、无统计埋点
- 崩溃日志自动写入手机 Download 目录「课程表崩溃日志.txt」，便于反馈问题

## 版本与更新

当前版本 **v2.11.7**。安装包与逐版本更新说明见 [Releases](https://github.com/liangleionline/CourseScheduleApp/releases)。

## 技术栈
- 原生 Android（Java 17），compileSdk 34 / minSdk 24
- AndroidX：AppCompat 1.6.1、Material 1.11.0、ConstraintLayout 2.1.4、ViewPager2 1.1.0、core-remoteviews 1.1.0、WorkManager 2.9.0
- 存储：SharedPreferences + JSON（org.json）

## 构建
```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk17
./gradlew assembleDebug     # 未签名调试包
./gradlew assembleRelease   # 需在项目根目录放置 keystore.properties（不随仓库分发）
```

## 项目结构
```
app/src/main/java/com/courseschedule/app/
  data/     # 数据模型、持久化、时间推算引擎、配色
  ui/       # 首页(MainActivity)、排课引导(SetupActivity)、设置(SettingsActivity)、课表视图(TimetableView)
  widget/   # 桌面小组件(4×3 / 4×4)、添加时配置页、WorkManager 定时刷新
  CrashApplication.java   # 全局崩溃日志捕获
```

## 开源声明与许可

本项目以 **GNU General Public License v3.0** 发布，许可证全文见 [LICENSE](./LICENSE)。

桌面小组件的实现参考并复刻自开源项目 [ShiGuangSchedule/shiguangschedule](https://github.com/ShiGuangSchedule/shiguangschedule)（Apache License 2.0，Copyright (C) 2025 XingHeYuZhuan）。引用范围与修改说明见 [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md)，其许可证全文见 [LICENSES/APACHE-2.0.txt](./LICENSES/APACHE-2.0.txt)。

## 数据统计

README 顶部徽章实时展示本项目的访问与下载情况：

- **最新版本 / 总下载 / 最新版下载 / APK 下载**：来自 GitHub Releases 附件的下载计数，由 shields.io 实时读取
- **Stars / Forks / 最近提交**：仓库社交数据与提交活动
- **README 访问量**：仓库主页被打开的次数（第三方计数器，每次访问 +1）

> 更详细的流量数据（浏览量、独立访客、克隆数、各 Release 下载明细）见仓库 **Insights → Traffic / Releases**，其中 Traffic 仅仓库所有者可见、仅保留最近 14 天。
