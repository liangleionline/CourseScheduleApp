package com.courseschedule.app.data;

/** 引擎计算后的一个可渲染格子（含绝对时间） */
public class RenderedCell {
    public int day;
    public int type; // 0 course, 1 noncourse
    public String refId;
    public int startMin;
    public int endMin;

    public RenderedCell(int day, int type, String refId, int startMin, int endMin) {
        this.day = day;
        this.type = type;
        this.refId = refId;
        this.startMin = startMin;
        this.endMin = endMin;
    }
}
