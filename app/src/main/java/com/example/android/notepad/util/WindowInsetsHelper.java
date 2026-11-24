package com.example.android.notepad.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 边到边（edge-to-edge）显示下的窗口内边距处理。
 * <p>
 * Android 15 起，targetSdk 大于 34 的应用默认全屏绘制，内容会被状态栏 / 导航栏遮挡，
 * 因此需要手动把系统栏高度加到对应 View 的内边距上。
 */
public final class WindowInsetsHelper {

    private WindowInsetsHelper() {
    }

    /** 顶部内边距 = 原有内边距 + 状态栏高度（含刘海屏） */
    public static void padTop(final View view) {
        final int basePadding = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            target.setPadding(target.getPaddingLeft(), basePadding + bars.top,
                    target.getPaddingRight(), target.getPaddingBottom());
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** 底部内边距 = 原有内边距 + 导航栏高度 */
    public static void padBottom(final View view) {
        final int basePadding = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            target.setPadding(target.getPaddingLeft(), target.getPaddingTop(),
                    target.getPaddingRight(), basePadding + bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** 底部内边距 = 原有内边距 + max(导航栏, 输入法)，用于编辑器避免被键盘遮挡 */
    public static void padBottomForIme(final View view) {
        final int basePadding = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(bars.bottom, ime.bottom);
            target.setPadding(target.getPaddingLeft(), target.getPaddingTop(),
                    target.getPaddingRight(), basePadding + bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }

    /** 底部外边距 = 原有外边距 + 导航栏高度，用于浮动按钮 */
    public static void marginBottom(final View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        final ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        final int baseMargin = margins.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            int bottom = baseMargin + bars.bottom;
            if (margins.bottomMargin != bottom) {
                margins.bottomMargin = bottom;
                target.setLayoutParams(margins);
            }
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
