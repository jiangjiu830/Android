package com.example.android.notepad.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.android.notepad.data.SortOrder;

/**
 * 偏好设置的读写封装（SharedPreferences）。
 */
public final class Prefs {

    private static final String FILE_NAME = "notepad_prefs";

    /** 主题：跟随系统 */
    public static final int THEME_SYSTEM = 0;
    /** 主题：浅色 */
    public static final int THEME_LIGHT = 1;
    /** 主题：深色 */
    public static final int THEME_DARK = 2;

    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_SORT = "sort_order";
    private static final String KEY_SHOW_PREVIEW = "show_preview";
    private static final String KEY_FONT_SIZE = "font_size";

    private static final int DEFAULT_FONT_SIZE = 16;

    private Prefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public static int themeMode(Context context) {
        return prefs(context).getInt(KEY_THEME, THEME_SYSTEM);
    }

    public static void setThemeMode(Context context, int mode) {
        prefs(context).edit().putInt(KEY_THEME, mode).apply();
    }

    public static SortOrder sortOrder(Context context) {
        return SortOrder.fromKey(prefs(context).getString(KEY_SORT, SortOrder.MODIFIED.key));
    }

    public static void setSortOrder(Context context, SortOrder order) {
        prefs(context).edit().putString(KEY_SORT, order.key).apply();
    }

    public static boolean showPreview(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_PREVIEW, true);
    }

    public static void setShowPreview(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_SHOW_PREVIEW, show).apply();
    }

    public static int fontSize(Context context) {
        return prefs(context).getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
    }

    public static void setFontSize(Context context, int sizeSp) {
        prefs(context).edit().putInt(KEY_FONT_SIZE, sizeSp).apply();
    }
}
