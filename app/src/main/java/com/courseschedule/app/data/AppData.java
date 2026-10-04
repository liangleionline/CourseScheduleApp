package com.courseschedule.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 应用数据单例：课程库、非课程库、排布记录、全局设置。
 * 本地持久化（SharedPreferences + JSON），重启不丢失。
 */
public class AppData {
    private static final String PREFS = "course_app";
    private static final String KEY_DATA = "data";
    private static final int DEFAULT_LESSON = 45;
    private static final int DEFAULT_FIRST_START = 8 * 60; // 08:00

    private static AppData instance;

    public List<Course> courses = new ArrayList<>();
    public List<NonCourseItem> nonCourses = new ArrayList<>();
    public List<ScheduleEntry> entries = new ArrayList<>();
    public int lessonDurationMin = DEFAULT_LESSON;
    public int firstStartMin = DEFAULT_FIRST_START;
    public boolean showWeekend = false;
    private long paletteSeed = 20260901L;

    private final SharedPreferences prefs;

    private AppData(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
    }

    public static AppData get(Context ctx) {
        if (instance == null) instance = new AppData(ctx);
        return instance;
    }

    /** 是否已有课表排布数据 */
    public boolean hasSchedule() {
        return !entries.isEmpty();
    }

    public Course getCourse(String id) {
        for (Course c : courses) if (c.id.equals(id)) return c;
        return null;
    }

    public NonCourseItem getNonCourse(String id) {
        for (NonCourseItem n : nonCourses) if (n.id.equals(id)) return n;
        return null;
    }

    /** 预置课程库（仅在课程库为空时首次注入） */
    public void preseedCoursesIfEmpty() {
        if (!courses.isEmpty()) return;
        int[] pal = ColorUtil.shuffledPalette(paletteSeed);
        String[] names = {"语文", "数学", "英语", "体育", "物理", "化学", "生物",
                "历史", "地理", "政治", "美术", "音乐", "劳动", "形体", "写字", "道德与法治"};
        int idx = 0;
        for (String n : names) {
            int bg = pal[idx % pal.length];
            idx++;
            courses.add(new Course(UUID.randomUUID().toString(), n, "", bg, ColorUtil.readableText(bg)));
        }
        persist();
    }

    /** 预置非课程库（仅在为空时首次注入） */
    public void preseedNonCoursesIfEmpty() {
        if (!nonCourses.isEmpty()) return;
        String[][] defs = {{"早读", "20"}, {"课间", "10"}, {"课间操", "20"}, {"午休", "60"}, {"眼保健操", "5"}};
        for (String[] d : defs) {
            nonCourses.add(new NonCourseItem(UUID.randomUUID().toString(), d[0], Integer.parseInt(d[1])));
        }
        persist();
    }

    /** 新增课程，自动分配配色 */
    public Course addCourse(String name, String teacher) {
        int[] pal = ColorUtil.shuffledPalette(paletteSeed);
        int used = courses.size();
        int bg = pal[used % pal.length];
        Course c = new Course(UUID.randomUUID().toString(), name, teacher, bg, ColorUtil.readableText(bg));
        courses.add(c);
        persist();
        return c;
    }

    public NonCourseItem addNonCourse(String name, int durationMin) {
        NonCourseItem n = new NonCourseItem(UUID.randomUUID().toString(), name, durationMin);
        nonCourses.add(n);
        persist();
        return n;
    }

    public void deleteCourse(String id) {
        courses.removeIf(c -> c.id.equals(id));
        entries.removeIf(e -> e.type == 0 && e.refId.equals(id));
        persist();
    }

    public void deleteNonCourse(String id) {
        nonCourses.removeIf(n -> n.id.equals(id));
        entries.removeIf(e -> e.type == 1 && e.refId.equals(id));
        persist();
    }

    /** 清空课表排布（保留课程库、非课程库、全局设置） */
    public void clearSchedule() {
        entries.clear();
        persist();
    }

    public void persist() {
        try {
            JSONObject root = new JSONObject();
            root.put("lesson", lessonDurationMin);
            root.put("firstStart", firstStartMin);
            root.put("weekend", showWeekend);
            root.put("seed", paletteSeed);

            JSONArray cs = new JSONArray();
            for (Course c : courses) {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("name", c.name); o.put("teacher", c.teacher);
                o.put("bg", c.bgColor); o.put("tc", c.textColor);
                cs.put(o);
            }
            root.put("courses", cs);

            JSONArray ns = new JSONArray();
            for (NonCourseItem n : nonCourses) {
                JSONObject o = new JSONObject();
                o.put("id", n.id); o.put("name", n.name); o.put("dur", n.durationMin);
                ns.put(o);
            }
            root.put("non", ns);

            JSONArray es = new JSONArray();
            for (ScheduleEntry e : entries) {
                JSONObject o = new JSONObject();
                o.put("d", e.day); o.put("t", e.type); o.put("r", e.refId);
                es.put(o);
            }
            root.put("entries", es);

            prefs.edit().putString(KEY_DATA, root.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void load() {
        String raw = prefs.getString(KEY_DATA, null);
        try {
            if (raw != null) {
                JSONObject root = new JSONObject(raw);
                lessonDurationMin = root.optInt("lesson", DEFAULT_LESSON);
                firstStartMin = root.optInt("firstStart", DEFAULT_FIRST_START);
                showWeekend = root.optBoolean("weekend", false);
                paletteSeed = root.optLong("seed", 20260901L);

                courses.clear();
                JSONArray cs = root.optJSONArray("courses");
                if (cs != null) for (int i = 0; i < cs.length(); i++) {
                    JSONObject o = cs.getJSONObject(i);
                    courses.add(new Course(o.getString("id"), o.optString("name"),
                            o.optString("teacher"), o.optInt("bg"), o.optInt("tc")));
                }
                nonCourses.clear();
                JSONArray ns = root.optJSONArray("non");
                if (ns != null) for (int i = 0; i < ns.length(); i++) {
                    JSONObject o = ns.getJSONObject(i);
                    nonCourses.add(new NonCourseItem(o.getString("id"), o.optString("name"), o.optInt("dur")));
                }
                entries.clear();
                JSONArray es = root.optJSONArray("entries");
                if (es != null) for (int i = 0; i < es.length(); i++) {
                    JSONObject o = es.getJSONObject(i);
                    entries.add(new ScheduleEntry(o.getInt("d"), o.getInt("t"), o.getString("r")));
                }
            }
        } catch (Exception ignored) {
        }
    }
}
