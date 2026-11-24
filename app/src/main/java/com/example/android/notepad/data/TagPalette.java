package com.example.android.notepad.data;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.example.android.notepad.R;

/**
 * 自定义标签的配色。新建标签时按当前标签数量依次取色，避免用户还要选颜色。
 */
public final class TagPalette {

    private static final int[] COLORS = {
            R.color.tag_color_1,
            R.color.tag_color_2,
            R.color.tag_color_3,
            R.color.tag_color_4,
            R.color.tag_color_5,
            R.color.tag_color_6,
            R.color.tag_color_7,
            R.color.tag_color_8
    };

    private TagPalette() {
    }

    /** 按序号取一个调色板颜色；序号会自动循环。 */
    public static int colorForIndex(Context context, int index) {
        int safeIndex = Math.floorMod(index, COLORS.length);
        return ContextCompat.getColor(context, COLORS[safeIndex]);
    }

    /** 未知标签（例如备份里缺少标签定义）使用的兜底颜色。 */
    public static int defaultColor(Context context) {
        return ContextCompat.getColor(context, R.color.category_default);
    }
}
