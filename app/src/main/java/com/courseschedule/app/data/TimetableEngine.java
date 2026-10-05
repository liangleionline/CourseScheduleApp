package com.courseschedule.app.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 时间推算引擎。
 * 规则：
 *  - 周一为模板：非课程项只配置在周一，其余天自动横向贯穿全周。
 *  - 周一首个项目由用户选开始时间（firstStartMin）；其后顺序接续。
 *  - 周二~周五：无需设置开始时间，复用周一起始时间；课程填入周一模板的空闲时段。
 *  - 每天课程数量可不同；修改非课程项时长 / 课时时长会自动重排。
 */
public class TimetableEngine {

    public static final int TYPE_COURSE = 0;
    public static final int TYPE_NONCOURSE = 1;

    /** 计算某一天的渲染格子（当前激活课程表） */
    public static List<RenderedCell> computeDay(AppData data, int day) {
        return computeDay(data, day, data.entries);
    }

    /** 计算某一天的渲染格子（指定课程表的排布，供小组件等多课程表场景使用） */
    public static List<RenderedCell> computeDay(AppData data, int day, List<ScheduleEntry> entries) {
        List<RenderedCell> result = new ArrayList<>();
        if (day == 1) {
            List<RenderedCell> monday = layoutMonday(data, entries);
            result.addAll(monday);
            return result;
        }
        // 其余天：非课程带来自周一模板（贯穿全周）+ 本天课程顺序填入「非课程带之间的课程时段」
        List<RenderedCell> monday = layoutMonday(data, entries);
        List<RenderedCell> bands = new ArrayList<>();
        for (RenderedCell c : monday) {
            if (c.type == TYPE_NONCOURSE) {
                bands.add(c);
                result.add(new RenderedCell(day, TYPE_NONCOURSE, c.refId, c.startMin, c.endMin));
            }
        }
        // 本天课程（按存储顺序）：从周一起始时间顺序放置，遇到贯穿全周的非课程带则跳过后继续
        List<ScheduleEntry> dayCourses = new ArrayList<>();
        for (ScheduleEntry e : entries) {
            if (e.day == day && e.type == TYPE_COURSE) dayCourses.add(e);
        }
        int t = data.firstStartMin;
        for (ScheduleEntry e : dayCourses) {
            int dur = data.lessonDurationMin;
            int start = t, end = t + dur;
            boolean overlap = true;
            while (overlap) {
                overlap = false;
                for (RenderedCell b : bands) {
                    if (end > b.startMin && start < b.endMin) {
                        start = b.endMin;
                        end = start + dur;
                        overlap = true;
                        break;
                    }
                }
            }
            result.add(new RenderedCell(day, TYPE_COURSE, e.refId, start, end));
            t = end;
        }
        Collections.sort(result, new Comparator<RenderedCell>() {
            @Override
            public int compare(RenderedCell a, RenderedCell b) {
                if (a.startMin != b.startMin) return a.startMin - b.startMin;
                return a.endMin - b.endMin;
            }
        });
        return result;
    }

    /** 布局周一：顺序接续 */
    private static List<RenderedCell> layoutMonday(AppData data, List<ScheduleEntry> entries) {
        List<RenderedCell> cells = new ArrayList<>();
        int t = data.firstStartMin;
        for (ScheduleEntry e : entries) {
            if (e.day != 1) continue;
            int dur;
            if (e.type == TYPE_COURSE) {
                dur = data.lessonDurationMin;
            } else {
                NonCourseItem n = data.getNonCourse(e.refId);
                dur = n == null ? 0 : n.durationMin;
            }
            if (dur <= 0) dur = 0;
            cells.add(new RenderedCell(1, e.type, e.refId, t, t + dur));
            t += dur;
        }
        return cells;
    }

    /** 计算全局时间范围（用于视图纵向坐标） */
    public static int[] globalTimeRange(AppData data, List<Integer> days) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int d : days) {
            for (RenderedCell c : computeDay(data, d)) {
                if (c.startMin < min) min = c.startMin;
                if (c.endMin > max) max = c.endMin;
            }
        }
        if (min == Integer.MAX_VALUE) { min = data.firstStartMin; max = data.firstStartMin + data.lessonDurationMin; }
        return new int[]{min, max};
    }
}
