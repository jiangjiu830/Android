package com.example.android.notepad.ui;

import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

/**
 * 分类小标签的着色工具：背景使用 16% 透明度的颜色，文字使用满色，保证深浅色主题下都清晰。
 */
public final class Pills {

    private static final int BACKGROUND_ALPHA = 40;

    private Pills() {
    }

    /**
     * @param view              目标 TextView（背景为圆角 shape）
     * @param textColor         文字颜色
     * @param backgroundColor   背景基色（会被加上透明度）
     */
    public static void tint(TextView view, int textColor, int backgroundColor) {
        view.setBackgroundTintList(
                ColorStateList.valueOf(ColorUtils.setAlphaComponent(backgroundColor, BACKGROUND_ALPHA)));
        view.setTextColor(textColor);
    }
}
