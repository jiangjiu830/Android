package com.example.android.notepad;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.android.notepad.util.Prefs;

/**
 * 应用入口：在界面创建之前应用用户选择的主题（跟随系统 / 浅色 / 深色）。
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(nightModeFor(Prefs.themeMode(this)));
    }

    /** 把偏好设置中的主题模式映射为 AppCompatDelegate 的夜间模式常量 */
    public static int nightModeFor(int themeMode) {
        switch (themeMode) {
            case Prefs.THEME_LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case Prefs.THEME_DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            case Prefs.THEME_SYSTEM:
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }
}
