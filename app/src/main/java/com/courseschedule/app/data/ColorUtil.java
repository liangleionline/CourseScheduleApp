package com.courseschedule.app.data;

/** 配色工具：随机但美观、可读、互不接近的课程配色 */
public class ColorUtil {

    // 一组经过挑选、互相区分度高的背景色（偏饱和，整体美观协调）
    private static final int[] PALETTE = {
            0xFFEF5350, 0xFFAB47BC, 0xFF5C6BC0, 0xFF42A5F5, 0xFF26A69A,
            0xFF66BB6A, 0xFFFFB300, 0xFFFB8C00, 0xFF8D6E63, 0xFFEC407A,
            0xFF7E57C2, 0xFF29B6F6, 0xFF9CCC65, 0xFFFF7043, 0xFF78909C,
            0xFFD32F2F, 0xFF388E3C, 0xFF0288D1, 0xFFF57C00, 0xFF6A1B9A,
            0xFF00695C, 0xFF4527A0, 0xFF5D4037, 0xFFC2185B, 0xFF37474F,
            0xFFE65100, 0xFF33691E, 0xFF8E24AA
    };

    // 非课程项统一浅色（与课程随机色明显区分）
    public static final int NONCOURSE_BG = 0xFFE9EEF5;
    public static final int NONCOURSE_TEXT = 0xFF334155;

    public static int[] shuffledPalette(long seed) {
        int[] p = PALETTE.clone();
        java.util.Random r = new java.util.Random(seed);
        for (int i = p.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        return p;
    }

    /** 根据背景亮度自动选择可读文字色（白或近黑），保证与背景非相近色 */
    public static int readableText(int bg) {
        int r = (bg >> 16) & 0xFF;
        int g = (bg >> 8) & 0xFF;
        int b = bg & 0xFF;
        double lum = 0.299 * r + 0.587 * g + 0.114 * b;
        return lum > 150 ? 0xFF212121 : 0xFFFFFFFF;
    }
}
