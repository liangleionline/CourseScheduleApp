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

    /** 一个课程表：名称 + 各自的每日排布 */
    public static class Timetable {
        public String id;
        public String name;
        public List<ScheduleEntry> entries = new ArrayList<>();
        Timetable(String id, String name) { this.id = id; this.name = name; }
    }

    public List<Course> courses = new ArrayList<>();
    public List<NonCourseItem> nonCourses = new ArrayList<>();
    public List<Timetable> timetables = new ArrayList<>();
    public String activeTimetableId = null;
    /** 当前激活课程表的排布（指向 timetables 中激活项的 entries，随切换更新） */
    public List<ScheduleEntry> entries = new ArrayList<>();
    public int lessonDurationMin = DEFAULT_LESSON;
    public int firstStartMin = DEFAULT_FIRST_START;
    public boolean showWeekend = false;
    private long paletteSeed = 20260901L;

    private final SharedPreferences prefs;
    private final Context appContext;

    private AppData(Context ctx) {
        appContext = ctx.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        syncEntriesRef();
    }

    public static AppData get(Context ctx) {
        if (instance == null) instance = new AppData(ctx);
        return instance;
    }

    public Timetable activeTimetable() {
        for (Timetable t : timetables) if (t.id.equals(activeTimetableId)) return t;
        return timetables.isEmpty() ? null : timetables.get(0);
    }

    /** 确保至少存在一个课程表 */
    public void ensureTimetable() {
        if (!timetables.isEmpty()) return;
        Timetable t = new Timetable(UUID.randomUUID().toString(), "我的课程表");
        timetables.add(t);
        activeTimetableId = t.id;
        syncEntriesRef();
        persist();
    }

    /** 切换当前激活课程表 */
    public void setActiveTimetable(String id) {
        for (Timetable t : timetables) {
            if (t.id.equals(id)) {
                activeTimetableId = id;
                syncEntriesRef();
                persist();
                return;
            }
        }
    }

    public Timetable addTimetable(String name) {
        Timetable t = new Timetable(UUID.randomUUID().toString(), name);
        timetables.add(t);
        persist();
        return t;
    }

    public void renameTimetable(String id, String name) {
        for (Timetable t : timetables) {
            if (t.id.equals(id)) { t.name = name; persist(); return; }
        }
    }

    public void deleteTimetable(String id) {
        if (timetables.size() <= 1) return; // 至少保留一个
        timetables.removeIf(t -> t.id.equals(id));
        if (id.equals(activeTimetableId)) {
            activeTimetableId = timetables.get(0).id;
        }
        syncEntriesRef();
        persist();
    }

    public int timetableIndex(String id) {
        for (int i = 0; i < timetables.size(); i++) {
            if (timetables.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    private void syncEntriesRef() {
        Timetable t = activeTimetable();
        entries = t == null ? new ArrayList<>() : t.entries;
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
        for (Timetable t : timetables) t.entries.removeIf(e -> e.type == 0 && e.refId.equals(id));
        persist();
    }

    public void deleteNonCourse(String id) {
        nonCourses.removeIf(n -> n.id.equals(id));
        for (Timetable t : timetables) t.entries.removeIf(e -> e.type == 1 && e.refId.equals(id));
        persist();
    }

    /** 清空当前激活课表的排布（保留课程库、非课程库、全局设置） */
    public void clearSchedule() {
        entries.clear();
        persist();
    }

    public void persist() {
        try {
            prefs.edit().putString(KEY_DATA, toJson().toString()).apply();
            // 数据变化时同步刷新桌面小组件
            com.courseschedule.app.widget.TimetableWidgetProvider.refreshAll(appContext);
        } catch (Exception ignored) {
        }
    }

    /** 导出全部数据（课程库、非课程库、排布、全局设置）为单个 JSON 字符串 */
    public String exportJson() {
        try {
            return toJson().toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** 从导出的 JSON 恢复全部数据（覆盖当前所有数据） */
    public boolean importJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
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

            timetables.clear();
            JSONArray ts = root.optJSONArray("timetables");
            if (ts != null && ts.length() > 0) {
                for (int i = 0; i < ts.length(); i++) {
                    JSONObject o = ts.getJSONObject(i);
                    Timetable t = new Timetable(o.optString("id"), o.optString("name", "我的课程表"));
                    JSONArray es = o.optJSONArray("entries");
                    if (es != null) for (int j = 0; j < es.length(); j++) {
                        JSONObject eo = es.getJSONObject(j);
                        t.entries.add(new ScheduleEntry(eo.getInt("d"), eo.getInt("t"), eo.getString("r")));
                    }
                    timetables.add(t);
                }
            } else {
                JSONArray es = root.optJSONArray("entries");
                Timetable t = new Timetable(UUID.randomUUID().toString(), "我的课程表");
                if (es != null) for (int i = 0; i < es.length(); i++) {
                    JSONObject o = es.getJSONObject(i);
                    t.entries.add(new ScheduleEntry(o.getInt("d"), o.getInt("t"), o.getString("r")));
                }
                timetables.add(t);
            }
            activeTimetableId = root.optString("active", timetables.get(0).id);
            syncEntriesRef();
            persist();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JSONObject toJson() throws Exception {
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

        JSONArray ts = new JSONArray();
        for (Timetable t : timetables) {
            JSONObject o = new JSONObject();
            o.put("id", t.id);
            o.put("name", t.name);
            JSONArray es = new JSONArray();
            for (ScheduleEntry e : t.entries) {
                JSONObject eo = new JSONObject();
                eo.put("d", e.day); eo.put("t", e.type); eo.put("r", e.refId);
                es.put(eo);
            }
            o.put("entries", es);
            ts.put(o);
        }
        root.put("timetables", ts);
        if (activeTimetableId != null) root.put("active", activeTimetableId);
        return root;
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

                timetables.clear();
                JSONArray ts = root.optJSONArray("timetables");
                if (ts != null && ts.length() > 0) {
                    // 新结构：多课程表
                    for (int i = 0; i < ts.length(); i++) {
                        JSONObject o = ts.getJSONObject(i);
                        Timetable t = new Timetable(o.optString("id"), o.optString("name", "我的课程表"));
                        JSONArray es = o.optJSONArray("entries");
                        if (es != null) for (int j = 0; j < es.length(); j++) {
                            JSONObject eo = es.getJSONObject(j);
                            t.entries.add(new ScheduleEntry(eo.getInt("d"), eo.getInt("t"), eo.getString("r")));
                        }
                        timetables.add(t);
                    }
                    activeTimetableId = root.optString("active", timetables.get(0).id);
                } else {
                    // 旧结构：单课程表，迁移为一个「我的课程表」
                    List<ScheduleEntry> legacy = new ArrayList<>();
                    JSONArray es = root.optJSONArray("entries");
                    if (es != null) for (int i = 0; i < es.length(); i++) {
                        JSONObject o = es.getJSONObject(i);
                        legacy.add(new ScheduleEntry(o.getInt("d"), o.getInt("t"), o.getString("r")));
                    }
                    Timetable t = new Timetable(UUID.randomUUID().toString(), "我的课程表");
                    t.entries.addAll(legacy);
                    timetables.add(t);
                    activeTimetableId = t.id;
                }
                syncEntriesRef();
            }
        } catch (Exception ignored) {
        }
    }
}
