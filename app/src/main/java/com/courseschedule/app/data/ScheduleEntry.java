package com.courseschedule.app.data;

/**
 * 排布记录（仅存顺序与引用，不存绝对时间）。
 * day: 1=周一 ... 7=周日
 * type: 0=课程项, 1=非课程项
 * refId: 指向 Course.id 或 NonCourseItem.id
 *
 * 时间由 TimetableEngine 依据「周一模板 + 全局课时时长 + 首节开始时间」统一推算。
 * 非课程项只配置在周一，其余天自动横向贯穿全周。
 */
public class ScheduleEntry {
    public int day;
    public int type; // 0 course, 1 noncourse
    public String refId;

    public ScheduleEntry(int day, int type, String refId) {
        this.day = day;
        this.type = type;
        this.refId = refId;
    }
}
