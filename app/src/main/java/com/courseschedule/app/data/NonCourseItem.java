package com.courseschedule.app.data;

/** 非课程项：贯穿全周的通用项目，如早读、课间、午休等，有独立时长 */
public class NonCourseItem {
    public String id;
    public String name;
    public int durationMin; // 独立时长（分钟）

    public NonCourseItem(String id, String name, int durationMin) {
        this.id = id;
        this.name = name;
        this.durationMin = durationMin;
    }
}
