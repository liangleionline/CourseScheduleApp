package com.courseschedule.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 应用数据单例：多课程表，每个课程表各自独立一套课程库、非课程库、课时时长与排布。
 * 顶层 courses/nonCourses/lessonDurationMin/firstStartMin 为「当前激活课程表」的快捷引用，
 * 切换激活课表时自动指向该课表的设置（对外调用方式不变）。
 * 本地持久化（SharedPreferences + JSON），重启不丢失。
 */
public class AppData {
    private static final String PREFS = "course_app";
    private static final String KEY_DATA = "data";
    private static final int DEFAULT_LESSON = 45;
    private static final int DEFAULT_FIRST_START = 8 * 60; // 08:00

    private static AppData instance;
    private static Context appContext;

    /** 一个课程表：名称 + 各自独立的课程库/非课程库/课时时长 + 每日排布 */
    public static class Timetable {
        public String id;
        public String name;
        public List<Course> courses = new ArrayList<>();
        public List<NonCourseItem> nonCourses = new ArrayList<>();
        public int lessonDurationMin;
        public int firstStartMin;
        public List<ScheduleEntry> entries = new ArrayList<>();
        Timetable(String id, String name) {
            this.id = id;
            this.name = name;
            this.lessonDurationMin = DEFAULT_LESSON;
            this.firstStartMin = DEFAULT_FIRST_START;
        }
    }

    /** 当前激活课程表的快捷引用（随切换更新） */
    public List<Course> courses = new ArrayList<>();
    public List<NonCourseItem> nonCourses = new ArrayList<>();
    public List<Timetable> timetables = new ArrayList<>();
    public String activeTimetableId = null;
    /** 当前激活课程表的排布（指向激活项的 entries，随切换更新） */
    public List<ScheduleEntry> entries = new ArrayList<>();
    public int lessonDurationMin = DEFAULT_LESSON;
    public int firstStartMin = DEFAULT_FIRST_START;
    public boolean showWeekend = false;
    private long paletteSeed = 20260901L;

    private final SharedPreferences prefs;

    private AppData(Context ctx) {
        appContext = ctx.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        load();
        syncActiveRefs();
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
        syncActiveRefs();
        persist();
    }

    /** 切换当前激活课程表（切换后快捷引用指向该课表的独立设置） */
    public void setActiveTimetable(String id) {
        for (Timetable t : timetables) {
            if (t.id.equals(id)) {
                activeTimetableId = id;
                syncActiveRefs();
                preseedCoursesIfEmpty();
                preseedNonCoursesIfEmpty();
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
        syncActiveRefs();
        persist();
    }

    public int timetableIndex(String id) {
        for (int i = 0; i < timetables.size(); i++) {
            if (timetables.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    /** 快捷引用同步：指向当前激活课程表的独立设置 */
    private void syncActiveRefs() {
        Timetable t = activeTimetable();
        if (t == null) {
            courses = new ArrayList<>();
            nonCourses = new ArrayList<>();
            entries = new ArrayList<>();
            lessonDurationMin = DEFAULT_LESSON;
            firstStartMin = DEFAULT_FIRST_START;
        } else {
            courses = t.courses;
            nonCourses = t.nonCourses;
            entries = t.entries;
            lessonDurationMin = t.lessonDurationMin;
            firstStartMin = t.firstStartMin;
        }
    }

    /** 计算指定课程表某天的格子（临时切换该课表的设置与排布，不改变当前激活状态，不持久化） */
    public List<RenderedCell> computeDayOf(String timetableId, int day) {
        Timetable t = null;
        for (Timetable tt : timetables) {
            if (tt.id.equals(timetableId)) { t = tt; break; }
        }
        if (t == null) return TimetableEngine.computeDay(this, day);
        List<ScheduleEntry> savedE = entries;
        List<Course> savedC = courses;
        List<NonCourseItem> savedN = nonCourses;
        int savedL = lessonDurationMin, savedF = firstStartMin;
        entries = t.entries;
        courses = t.courses;
        nonCourses = t.nonCourses;
        lessonDurationMin = t.lessonDurationMin;
        firstStartMin = t.firstStartMin;
        try {
            return TimetableEngine.computeDay(this, day);
        } finally {
            entries = savedE;
            courses = savedC;
            nonCourses = savedN;
            lessonDurationMin = savedL;
            firstStartMin = savedF;
        }
    }

    /** 是否存在指定 id 的课程表 */
    public boolean containsTimetable(String id) {
        for (Timetable t : timetables) if (t.id.equals(id)) return true;
        return false;
    }

    /** 是否已有课表排布数据（当前激活课表） */
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

    /** 预置课程库（当前激活课表为空时首次注入默认课程） */
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

    /** 预置非课程库（当前激活课表为空时首次注入默认项） */
    public void preseedNonCoursesIfEmpty() {
        if (!nonCourses.isEmpty()) return;
        String[][] defs = {{"早读", "20"}, {"课间", "10"}, {"课间操", "20"}, {"午休", "60"}, {"眼保健操", "5"}};
        for (String[] d : defs) {
            nonCourses.add(new NonCourseItem(UUID.randomUUID().toString(), d[0], Integer.parseInt(d[1])));
        }
        persist();
    }

    /** 新增课程（加入当前激活课表的课程库），自动分配配色 */
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
        // 课程属于当前激活课表：只清理该课表内的课程与引用
        courses.removeIf(c -> c.id.equals(id));
        entries.removeIf(e -> e.type == 0 && e.refId.equals(id));
        persist();
    }

    public void deleteNonCourse(String id) {
        nonCourses.removeIf(n -> n.id.equals(id));
        entries.removeIf(e -> e.type == 1 && e.refId.equals(id));
        persist();
    }

    /** 清空当前激活课表的排布（保留该课表的课程库、非课程库与设置） */
    public void clearSchedule() {
        entries.clear();
        persist();
    }

    /** 清空当前激活课表的全部数据（排布、课程库、非课程库、恢复默认课时），用于彻底重排 */
    public void clearTimetableData() {
        Timetable t = activeTimetable();
        if (t == null) return;
        t.entries.clear();
        t.courses.clear();
        t.nonCourses.clear();
        t.lessonDurationMin = DEFAULT_LESSON;
        t.firstStartMin = DEFAULT_FIRST_START;
        syncActiveRefs();
        persist();
    }

    public void persist() {
        try {
            prefs.edit().putString(KEY_DATA, toJson().toString()).apply();
        } catch (Exception ignored) {
        }
        // 数据变化后同步刷新桌面小组件
        try {
            if (appContext != null) com.courseschedule.app.widget.TimetableWidgetProvider.refreshAll(appContext);
        } catch (Exception ignored) {
        }
    }

    /** 导出全部数据（多课程表及各自独立设置）为单个 JSON 字符串 */
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
            showWeekend = root.optBoolean("weekend", false);
            paletteSeed = root.optLong("seed", 20260901L);

            // 旧格式顶层课程库/非课程库/时长（迁移：复制给所有课程表）
            List<Course> legacyCourses = parseCourses(root.optJSONArray("courses"));
            List<NonCourseItem> legacyNon = parseNonCourses(root.optJSONArray("non"));
            int legacyLesson = root.optInt("lesson", DEFAULT_LESSON);
            int legacyFirst = root.optInt("firstStart", DEFAULT_FIRST_START);

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
                    // 课程表独立设置；旧数据无独立设置时沿用顶层旧全局设置
                    List<Course> tc = parseCourses(o.optJSONArray("courses"));
                    t.courses = tc == null ? copyCourses(legacyCourses) : tc;
                    List<NonCourseItem> tn = parseNonCourses(o.optJSONArray("non"));
                    t.nonCourses = tn == null ? copyNon(legacyNon) : tn;
                    t.lessonDurationMin = o.optInt("lesson", legacyLesson);
                    t.firstStartMin = o.optInt("firstStart", legacyFirst);
                    timetables.add(t);
                }
            } else {
                // 更旧结构：单课程表排布，迁移为一个「我的课程表」
                Timetable t = new Timetable(UUID.randomUUID().toString(), "我的课程表");
                JSONArray es = root.optJSONArray("entries");
                if (es != null) for (int i = 0; i < es.length(); i++) {
                    JSONObject o = es.getJSONObject(i);
                    t.entries.add(new ScheduleEntry(o.getInt("d"), o.getInt("t"), o.getString("r")));
                }
                t.courses = copyCourses(legacyCourses);
                t.nonCourses = copyNon(legacyNon);
                t.lessonDurationMin = legacyLesson;
                t.firstStartMin = legacyFirst;
                timetables.add(t);
            }
            activeTimetableId = root.optString("active", timetables.get(0).id);
            syncActiveRefs();
            persist();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JSONObject toJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("weekend", showWeekend);
        root.put("seed", paletteSeed);

        JSONArray ts = new JSONArray();
        for (Timetable t : timetables) {
            JSONObject o = new JSONObject();
            o.put("id", t.id);
            o.put("name", t.name);
            o.put("lesson", t.lessonDurationMin);
            o.put("firstStart", t.firstStartMin);
            JSONArray cs = new JSONArray();
            for (Course c : t.courses) {
                JSONObject co = new JSONObject();
                co.put("id", c.id); co.put("name", c.name); co.put("teacher", c.teacher);
                co.put("bg", c.bgColor); co.put("tc", c.textColor);
                cs.put(co);
            }
            o.put("courses", cs);
            JSONArray ns = new JSONArray();
            for (NonCourseItem n : t.nonCourses) {
                JSONObject no = new JSONObject();
                no.put("id", n.id); no.put("name", n.name); no.put("dur", n.durationMin);
                ns.put(no);
            }
            o.put("non", ns);
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
                showWeekend = root.optBoolean("weekend", false);
                paletteSeed = root.optLong("seed", 20260901L);

                // 旧格式顶层课程库/非课程库/时长（迁移：复制给所有课程表）
                List<Course> legacyCourses = parseCourses(root.optJSONArray("courses"));
                List<NonCourseItem> legacyNon = parseNonCourses(root.optJSONArray("non"));
                int legacyLesson = root.optInt("lesson", DEFAULT_LESSON);
                int legacyFirst = root.optInt("firstStart", DEFAULT_FIRST_START);

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
                        List<Course> tc = parseCourses(o.optJSONArray("courses"));
                        t.courses = tc == null ? copyCourses(legacyCourses) : tc;
                        List<NonCourseItem> tn = parseNonCourses(o.optJSONArray("non"));
                        t.nonCourses = tn == null ? copyNon(legacyNon) : tn;
                        t.lessonDurationMin = o.optInt("lesson", legacyLesson);
                        t.firstStartMin = o.optInt("firstStart", legacyFirst);
                        timetables.add(t);
                    }
                    activeTimetableId = root.optString("active", timetables.get(0).id);
                } else {
                    // 更旧结构：单课程表排布，迁移为一个「我的课程表」
                    List<ScheduleEntry> legacy = new ArrayList<>();
                    JSONArray es = root.optJSONArray("entries");
                    if (es != null) for (int i = 0; i < es.length(); i++) {
                        JSONObject o = es.getJSONObject(i);
                        legacy.add(new ScheduleEntry(o.getInt("d"), o.getInt("t"), o.getString("r")));
                    }
                    Timetable t = new Timetable(UUID.randomUUID().toString(), "我的课程表");
                    t.entries.addAll(legacy);
                    t.courses = copyCourses(legacyCourses);
                    t.nonCourses = copyNon(legacyNon);
                    t.lessonDurationMin = legacyLesson;
                    t.firstStartMin = legacyFirst;
                    timetables.add(t);
                    activeTimetableId = t.id;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private List<Course> parseCourses(JSONArray arr) {
        if (arr == null) return null;
        List<Course> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Course(o.getString("id"), o.optString("name"),
                        o.optString("teacher"), o.optInt("bg"), o.optInt("tc")));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private List<NonCourseItem> parseNonCourses(JSONArray arr) {
        if (arr == null) return null;
        List<NonCourseItem> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject o = arr.getJSONObject(i);
                list.add(new NonCourseItem(o.getString("id"), o.optString("name"), o.optInt("dur")));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private List<Course> copyCourses(List<Course> src) {
        List<Course> out = new ArrayList<>();
        if (src != null) for (Course c : src) {
            out.add(new Course(c.id, c.name, c.teacher, c.bgColor, c.textColor));
        }
        return out;
    }

    private List<NonCourseItem> copyNon(List<NonCourseItem> src) {
        List<NonCourseItem> out = new ArrayList<>();
        if (src != null) for (NonCourseItem n : src) {
            out.add(new NonCourseItem(n.id, n.name, n.durationMin));
        }
        return out;
    }
}
