# 第三方代码声明 / Third-Party Notices

本应用引用了以下开源项目的代码，特此声明并致谢：

## ShiGuangSchedule / shiguangschedule

- **项目地址**：https://github.com/ShiGuangSchedule/shiguangschedule
- **版权方**：Copyright (C) 2025 XingHeYuZhuan
- **开源协议**：Apache License 2.0（全文见本仓库 `LICENSES/APACHE-2.0.txt`）

### 引用范围

本应用中的**安卓桌面小组件（widget）**实现，包括但不限于以下内容，均复刻自上述项目：

- 小组件布局结构（双日「今天/明天」课程卡片、课程条目、加载占位、假期覆盖视图）
- 渲染流程（ListView + `RemoteViewsCompat.RemoteCollectionItems`、空态/列表切换、PendingIntent 模板）
- `WorkManager` 每 15 分钟定期刷新机制
- 相关配色与圆角背景等视觉资源

### 修改说明

在原实现基础上做了如下修改（Java 重写 + 数据适配）：

- 将原 Kotlin 实现改写为 Java
- 数据源替换为本应用的课程表数据（课程名称、时间段、任课教师、课程配色）
- 移除原项目的周次（第 N 周）显示与教室地点字段（本应用无对应设置项）
- 适配 4×3 桌面尺寸并调整条目高度

---

依据 Apache License 2.0 第 4 条，向所有获得本应用副本的使用者提供上述声明与许可证文本。
