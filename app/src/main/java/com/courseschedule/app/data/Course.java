package com.courseschedule.app.data;

/** 课程项：一门课程 */
public class Course {
    public String id;
    public String name;
    public String teacher;
    public int bgColor;
    public int textColor;

    public Course(String id, String name, String teacher, int bgColor, int textColor) {
        this.id = id;
        this.name = name;
        this.teacher = teacher == null ? "" : teacher;
        this.bgColor = bgColor;
        this.textColor = textColor;
    }
}
