package com.courseschedule.app.widget;

/**
 * 4×4 桌面小组件 Provider。
 * 与 4×3（TimetableWidgetProvider）共用全部渲染逻辑，仅组件名不同以便在桌面显示为两个小组件；
 * 渲染时按各自 widgetId 的实际尺寸（getAppWidgetOptions）自动计算行高。
 */
public class TimetableWidgetProvider4x4 extends TimetableWidgetProvider {
}
