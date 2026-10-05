# 课程表 App（Android）

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

